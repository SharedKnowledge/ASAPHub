package net.sharksystem.hub.hubside;

import net.sharksystem.asap.ASAPException;
import net.sharksystem.hub.Connector;
import net.sharksystem.hub.protocol.ConnectorThread;
import net.sharksystem.utils.Commandline;
import net.sharksystem.utils.Log;

import java.io.IOException;
import java.net.*;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class ASAPTCPHub extends HubSingleEntitySharedChannel implements Runnable {
    public static final int DEFAULT_MAX_IDLE_CONNECTION_IN_SECONDS = 60;
    private final boolean createNewConnection;
    private int maxIdleInMillis = DEFAULT_MAX_IDLE_CONNECTION_IN_SECONDS * 1000;

    private final int port;
    private final ServerSocket serverSocket;
    private int minPort = 0;
    private int maxPort = 0;
    private int nextPort = 0;

    private boolean killed = false;
    private StatusPrinter statusPrinter;

    /**
     * Create a TCP hub that shares connections listening on default port.
     * @throws IOException
     */
    public ASAPTCPHub() throws IOException {
        this(DEFAULT_PORT);
    }

    /**
     * Create a TCP hub that always shares a connection
     * @param port
     * @throws IOException
     */
    public ASAPTCPHub(int port) throws IOException {
        this(port, false);
    }

    /**
     * Create a hub commincating with TCP
     * @param port proposed hub - during startup, hub will try to allocate that port but look for others if already taken
     * @param createNewConnection create a new TCP connnection or when peer ask for initiation of an encounter.
     * @throws IOException
     */
    public ASAPTCPHub(int port, boolean createNewConnection) throws IOException {
        this.port = port;
        this.nextPort = port+1;
        this.serverSocket = new ServerSocket(this.port);
        this.createNewConnection = createNewConnection;
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

    synchronized ServerSocket getServerSocket() throws IOException {
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
        Log.writeLog(this, "started on port: " + this.port);
        while(!killed) {
            Socket newConnection = null;
            try {
                newConnection = this. serverSocket.accept();
            }
            catch(IOException ioe) {
                Log.writeLog(this, "exception when going to accept TCP connections - fatal, give up: "
                        + ioe.getLocalizedMessage());
                return;
            }

            Log.writeLog(this, "new TCP connection - launch hub connector session");

            try {
                Connector hubConnectorSession;
                if(this.createNewConnection) {
                    hubConnectorSession =
                            new MultipleTCPChannelsConnectorHubSideImpl(
                                    newConnection.getInputStream(), newConnection.getOutputStream(), this);
                } else {
                    // another connector has connected
                    hubConnectorSession = new SharedChannelConnectorHubSide(
                            newConnection.getInputStream(), newConnection.getOutputStream(), this);
                }
                (new ConnectorThread(hubConnectorSession, newConnection.getInputStream())).start();
            } catch (IOException | ASAPException e) {
                // gone
                Log.writeLog(this, "hub connector session ended: " + e.getLocalizedMessage());
            }
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

    /////////////////////////////////////////////////////////////////////////////////////////////////////////
    //                                              command line                                           //
    /////////////////////////////////////////////////////////////////////////////////////////////////////////

    public static void main(String[] args) throws IOException {
        String usageString = "optional parameters: -port [portnumber] -maxIdleSeconds [seconds]";

        // now get real parameters
        HashMap<String, String> argumentMap = Commandline.parametersToMap(args,
                false, usageString);

        int port = DEFAULT_PORT;
        int maxIdleInSeconds = -1;

        if(argumentMap != null) {
            Set<String> keys = argumentMap.keySet();
            if(keys.contains("-help") || keys.contains("-?")) {
                System.out.println(usageString);
                System.exit(0);
            }

            // port defined
            String portString = argumentMap.get("-port");
            if(portString != null) {
                try {
                    port = Integer.parseInt(portString);
                } catch (RuntimeException re) {
                    System.err.println("port number must be a numeric: " + portString);
                    System.exit(0);
                }
            }

            // max idle in seconds?
            String maxIdleInSecondsString = argumentMap.get("-maxIdleInSeconds");
            if(maxIdleInSecondsString != null) {
                try {
                    maxIdleInSeconds = Integer.parseInt(maxIdleInSecondsString);
                } catch (RuntimeException re) {
                    System.err.println("maxIdleInSeconds must be a numeric: " + maxIdleInSecondsString);
                    System.exit(0);
                }
            }
        }

        // create TCPHub
        ASAPTCPHub.startTCPHubThread(port, true, maxIdleInSeconds);
    }

    public static ASAPTCPHub startTCPHubThread(int port, boolean multichannel, int maxIdleInSeconds)
            throws IOException {

        ASAPTCPHub tcpHub = new ASAPTCPHub(port, multichannel);
        if(maxIdleInSeconds > 0) {
            tcpHub.setMaxIdleConnectionInSeconds(maxIdleInSeconds);
        }
        System.out.printf("start TCP hub on %s:%d with maxIdleInSeconds: %d%n", tcpHub.getHostAddress(),
                tcpHub.port, tcpHub.maxIdleInMillis);
        tcpHub.startStatusPrinter();
        new Thread(tcpHub).start();
        return tcpHub;
    }

    /**
     * Gets the host address of the machine where the hub is running on.
     * @return host address (IPv4) as String; returns an empty string if host address couldn't be resolved
     */
    String getHostAddress(){
        String hostAddress = "";
        try (final DatagramSocket socket = new DatagramSocket()) {
            socket.connect(InetAddress.getByName("8.8.8.8"), 10002);
            hostAddress = socket.getLocalAddress().getHostAddress();
        } catch (SocketException e) {
            Log.writeLog(this, "cannot determine host address: " + e.getLocalizedMessage());
        } catch (UnknownHostException e) {
            throw new RuntimeException(e);
        }
        return hostAddress;
    }

    public void kill() {
        this.killed = true;

        try {
            this.serverSocket.close();
        } catch (IOException e) {
            Log.writeLog(this, "cannot close server socket: " + e.getLocalizedMessage());
        }

        if(this.statusPrinter != null) {
            this.statusPrinter.kill();
        }
    }

    private void startStatusPrinter() {
        this.statusPrinter = new StatusPrinter(this);
        this.statusPrinter.start();
    }

    private static class StatusPrinter extends Thread {
        private final ASAPTCPHub tcpHub;
        private boolean stopped = false;

        StatusPrinter(ASAPTCPHub tcpHub) {
            this.tcpHub = tcpHub;
        }

        public void run() {
            while(!this.stopped) {
                System.out.println("registered peers: " + this.tcpHub.getRegisteredPeers());
                try {
                    Thread.sleep(30000);
                } catch (InterruptedException e) {
                    // ignore
                }
            }
        }

        public void kill() {
            this.stopped = true;
        }
    }
}