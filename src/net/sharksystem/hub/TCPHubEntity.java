package net.sharksystem.hub;

import net.sharksystem.asap.ASAPException;
import net.sharksystem.asap.ASAPPeer;
import net.sharksystem.utils.Log;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class TCPHubEntity extends Thread implements HubEntity, Hub {
    public static final int DEFAULT_MAX_IDLE_CONNECTION_IN_SECONDS = 60;
    private int maxIdleInMillis = DEFAULT_MAX_IDLE_CONNECTION_IN_SECONDS * 1000;

    private final int port;
    private final ServerSocket serverSocket;
    private int minPort = 0;
    private int maxPort = 0;
    private int nextPort = 0;
    private Map<CharSequence, HubSession> hubSessions = new HashMap<>();

    public TCPHubEntity() throws IOException {
        this(DEFAULT_PORT);
    }

    public TCPHubEntity(int port) throws IOException {
        this.port = port;
        this.nextPort = port+1;
        this.serverSocket = new ServerSocket(this.port);
    }

    public void setPortRange(int minPort, int maxPort) throws ASAPException {
        if(minPort < -1 || maxPort < -1 || maxPort <= minPort) {
            throw new ASAPException("port number must be > 0 and max > min");
        }

        this.minPort = minPort;
        this.maxPort = maxPort;

        this.nextPort = this.minPort;
    }

    public void setMaxIdleConnectionInSeconds(int maxIdleInSeconds) {
        this.maxIdleInMillis = maxIdleInSeconds * 1000;
    }

    private synchronized ServerSocket getServerSocket() throws IOException {
        if(this.minPort == 0 || this.maxPort == 0) {
            return new ServerSocket(0);
        }

        int port = this.nextPort++;
        // try
        while(port <= this.maxPort) {
            try {
                ServerSocket srv = new ServerSocket(port);
                return srv;
            } catch (IOException ioe) {
                // port already in use
            }
            port = this.nextPort++;
        }
        this.nextPort = this.minPort; // rewind for next round
        throw new IOException("all ports are in use");
    }

    @Override
    public void run() {
        try {
            while(true) {
                Socket newConnection = this.serverSocket.accept();

                // another connector has connected
                HubSession hubSession = new HubSession(
                        newConnection.getInputStream(),newConnection.getOutputStream(), this);

                // start session
                hubSession.start();
            }
        } catch (IOException | ASAPException e) {
            // gone
            Log.writeLog(this, "TCP Hub died: " + e.getLocalizedMessage());
        }
    }

    @Override
    public void addASAPPeer(ASAPPeer peer) {
        // TODO
    }

    @Override
    public void removeASAPPeer(ASAPPeer peer) {
        // TODO
    }


    @Override
    public boolean isRegistered(CharSequence peerID) {
        return this.hubSessions.keySet().contains(peerID);
    }

    @Override
    public Set<CharSequence> getRegisteredPeerIDs() {
        return this.hubSessions.keySet();
    }

    @Override
    public void sessionStarted(CharSequence peerID, HubSession hubSession) {
        this.hubSessions.put(peerID, hubSession);
    }

    @Override
    public void sessionEnded(CharSequence peerID, HubSession hubSession) {
        this.hubSessions.remove(peerID);
    }

    Map<CharSequence, Set<CharSequence>> connectionRequests = new HashMap<>();

    private String connectionRequestsToString() {
        StringBuilder sb = new StringBuilder();
        sb.append("connection requests: ");

        Set<CharSequence> peerIDs = this.connectionRequests.keySet();
        if(peerIDs == null || peerIDs.isEmpty()) {
            sb.append("empty");
        } else {
            for(CharSequence peerID : peerIDs) {
                sb.append("\n"); sb.append(peerID); sb.append(": ");
                Set<CharSequence> otherPeerIDs = this.connectionRequests.get(peerID);
                boolean first = true;
                for(CharSequence otherPeerID : otherPeerIDs) {
                    if(first) { first = false; } else {sb.append(", ");}
                    sb.append(otherPeerID);
                }
            }
        }

        return sb.toString();
    }

    /**
     * Remember that peerA wants to connect to peerB
     * @param peerA
     * @param peerB
     */
    private void rememberConnectionRequest(CharSequence peerA, CharSequence peerB) {
        synchronized (this.connectionRequests) {
            Set<CharSequence> otherPeers = this.connectionRequests.get(peerA);
            if (otherPeers == null) {
                otherPeers = new HashSet<>();
                this.connectionRequests.put(peerA, otherPeers);
            }

            otherPeers.add(peerB);
        }
    }

    /** single communication channel version */
    @Override
    public void connectionRequest(CharSequence targetPeerID, HubSession sourceHubSession)
            throws ASAPException, IOException {

        Log.writeLog(this, "connection request from | to : "
                + sourceHubSession.getPeerID() + " | " + targetPeerID);

        HubSession targetHubSession = this.hubSessions.get(targetPeerID);
        if(targetHubSession == null) {
            return;
//            throw new ASAPException("no session");
        }

        // remember connection request
        this.rememberConnectionRequest(sourceHubSession.getPeerID(), targetPeerID);
        this.rememberConnectionRequest(targetPeerID, sourceHubSession.getPeerID());

        // ask both session to get quiet - need threads - hub would stop otherwise
        sourceHubSession.silentRQ(this.maxIdleInMillis * 2);
        targetHubSession.silentRQ(this.maxIdleInMillis * 2);
    }

    public void notifySilent(HubSession hubSession) {
        CharSequence silentPeerID = hubSession.getPeerID();
        Log.writeLog(this, "notified silent from: " + silentPeerID);
        HubSession otherSilentSession = null;

        //Log.writeLog(this, this.connectionRequestsToString());

        synchronized (this.connectionRequests) {
            Set<CharSequence> otherPeers = this.connectionRequests.get(silentPeerID);
            if(otherPeers != null) {
                for(CharSequence otherPeerID : otherPeers) {
                    HubSession otherSession = this.hubSessions.get(otherPeerID);
                    if(otherSession.isSilent()) {
                        Log.writeLog(this, "found matching silent session: " + otherPeerID);
                        otherSilentSession = otherSession;
                        // we have a match
                        otherPeers.remove(otherPeerID);

                        Set<CharSequence> peersOfOtherPeer = this.connectionRequests.get(otherPeerID);
                        if(peersOfOtherPeer != null) {
                            // must not null - but just in case
                            peersOfOtherPeer.remove(silentPeerID);
                        }
                    }
                }
            }
        }

        if(otherSilentSession != null) {
            // launch data session
            Log.writeLog(this, "ask to open data session "
                    + otherSilentSession.getPeerID() + " | " + hubSession.getPeerID());

            try {
                hubSession.openDataSession(otherSilentSession);
                otherSilentSession.openDataSession(hubSession);
            } catch (IOException e) {
                Log.writeLogErr(this, "cannot launch data session: " + e.getLocalizedMessage());
            }
        }
    }

    @Override
    public long getMaxIdleInMillis() {
        return this.maxIdleInMillis;
    }
}