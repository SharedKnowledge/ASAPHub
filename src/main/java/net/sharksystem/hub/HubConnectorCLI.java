package net.sharksystem.hub;

import net.sharksystem.asap.ASAPException;
import net.sharksystem.hub.peerside.HubConnector;
import net.sharksystem.hub.peerside.NewConnectionListener;
import net.sharksystem.hub.peerside.SharedTCPChannelConnectorPeerSide;
import net.sharksystem.streams.StreamPair;

import java.io.*;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

public class HubConnectorCLI {
    static final String GET_PEERS = "peers";
    static final String SET_PEER = "id";
    static final String CONNECT_PEER = "connect";

    private final PrintStream printStream;
    private final InputStream inputStream;
    private int port = 6000;
    //    private String hostName = "localhost";
    private String hostName = "localhost";


    public HubConnectorCLI(InputStream inputStream, OutputStream outputStream) {
        printStream = new PrintStream(outputStream);
        this.inputStream = inputStream;
    }

    public HubConnectorCLI(InputStream inputStream, OutputStream outputStream, String host, int port) {
        this(inputStream, outputStream);
        this.port = port;
        this.hostName = host;
    }

    public void startCLI() throws IOException, ASAPHubException, InterruptedException {
        CLIConnectionListener connectionListener = new CLIConnectionListener(this.printStream);
        Socket s = new Socket(hostName, port);
        HubConnector hubConnector = new SharedTCPChannelConnectorPeerSide(s, hostName, port, true);
        hubConnector.addListener(connectionListener);

        try (BufferedReader in =
                     new BufferedReader(new InputStreamReader(inputStream))) {
            String line;
            printStream.println("connector CLI started");
            while ((line = in.readLine()) != null) {
                String command = line;
                String arg = "";
                // get command and argument
                if (command.contains("=")) {
                    String[] commandAndArg = command.split("=", 2);
                    command = commandAndArg[0];
                    arg = commandAndArg[1];
                }
                printStream.println(command);
                printStream.println(arg);

                switch (command) {
                    case GET_PEERS:
                        hubConnector.syncHubInformation();
                        // wait for async reply of syncHubInformation
                        Thread.sleep(500);
                        printStream.println(hubConnector.getPeerIDs());
                        break;
                    case SET_PEER:
                        printStream.println("set peer-id to: " + arg);
                        hubConnector.connectHub(arg, true);
                        break;
                    case CONNECT_PEER:
                        printStream.println("connecting to peer: " + arg);
                        connectionListener.addMessage("Hello World!");
                        hubConnector.connectPeer(arg);
                        break;
                }
            }
        } catch (ASAPException e) {
            throw new RuntimeException(e);
        }
    }

    public static void main(String[] args) throws ASAPException, IOException, InterruptedException {
        PrintStream o = new PrintStream("log.txt");
        PrintStream console = System.out;

        // write logs into file
        System.setOut(o);

        HubConnectorCLI cli;
        if(args.length == 2){
            cli = new HubConnectorCLI(System.in, console, args[0], Integer.parseInt(args[1]));

        }else{
             cli = new HubConnectorCLI(System.in, console);
        }
        cli.startCLI();

    }
}

class CLIConnectionListener implements NewConnectionListener {
    private final PrintStream printStream;

    private final List<String> messages;

    public CLIConnectionListener(PrintStream printStream) {
        this.printStream = printStream;
        messages = new ArrayList<>();
    }

    public void addMessage(String message) {
        messages.add(message);
    }

    @Override
    public void notifyPeerConnected(CharSequence targetPeerID, StreamPair streamPair) {
        try {

            if (messages.size() > 0) {
                PrintStream ps = new PrintStream(streamPair.getOutputStream());
                ps.println(messages.get(0));
                messages.clear();
            }
            printStream.println("connected to peer: " + targetPeerID);
            BufferedReader reader = new BufferedReader(new InputStreamReader(streamPair.getInputStream()));
            printStream.println("received message: " + reader.readLine());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
