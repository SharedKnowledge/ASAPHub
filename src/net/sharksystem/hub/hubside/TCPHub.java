package net.sharksystem.hub.hubside;

import net.sharksystem.asap.ASAPException;
import net.sharksystem.utils.Log;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class TCPHub extends HubSingleEntity implements Runnable {
    public static final int DEFAULT_MAX_IDLE_CONNECTION_IN_SECONDS = 60;
    private int maxIdleInMillis = DEFAULT_MAX_IDLE_CONNECTION_IN_SECONDS * 1000;

    private final int port;
    private final ServerSocket serverSocket;
    private int minPort = 0;
    private int maxPort = 0;
    private int nextPort = 0;

    public TCPHub() throws IOException {
        this(DEFAULT_PORT);
    }

    public TCPHub(int port) throws IOException {
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
                SharedStreamPairConnectorHubSideImpl hubConnectorSession = new SharedStreamPairConnectorHubSideImpl(
                        newConnection.getInputStream(),newConnection.getOutputStream(), this);
            }
        } catch (IOException | ASAPException e) {
            // gone
            Log.writeLog(this, "TCP Hub died: " + e.getLocalizedMessage());
        }
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
}