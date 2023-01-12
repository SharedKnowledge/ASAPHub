package net.sharksystem.hub;

import net.sharksystem.asap.ASAPException;
import net.sharksystem.hub.peerside.HubConnector;
import net.sharksystem.hub.peerside.NewConnectionListener;
import net.sharksystem.hub.peerside.SharedTCPChannelConnectorPeerSide;
import net.sharksystem.streams.StreamPair;
import net.sharksystem.utils.Commandline;

import java.io.*;
import java.net.Socket;
import java.util.*;

public class HubConnectorCLI {
    static final String GET_PEERS = "peers";
    static final String SET_PEER = "set-id";
    static final String CONNECT_PEER = "connect";
    static final String HELP = "help";
    static final String EXIT = "exit";
    private final PrintStream printStream;
    private final InputStream inputStream;
    private int port;
    static int DEFAULT_PORT = 6000;
    //    private String hostName = "localhost";
    private String hostName;

    private static String helpText =
            "list available peers:         peers\n" +
                    "set own peer-id               set-id [peer-id] [multichannel false/true]\n" +
                    "connect to peer               connect [target-peer-id]\n" +
                    "get cli guidelines            help\n" +
                    "terminate application         exit";


    public HubConnectorCLI(InputStream inputStream, OutputStream outputStream, String host, int port) {
        printStream = new PrintStream(outputStream);
        this.inputStream = inputStream;
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
                List<String> args = new ArrayList<>();
                // get command and argument
                String[] commandAndArg = command.split(" ");
                command = commandAndArg[0];
                if (commandAndArg.length > 1)
                    args = new ArrayList<>(Arrays.asList(Arrays.copyOfRange(commandAndArg, 1, commandAndArg.length)));
                printStream.println(command);

                switch (command) {
                    case GET_PEERS:
                        hubConnector.syncHubInformation();
                        // wait for async reply of syncHubInformation
                        Thread.sleep(500);
                        printStream.println(hubConnector.getPeerIDs());
                        break;
                    case SET_PEER:
                        if (args.size() < 2) {
                            printStream.println("multichannel parameter was not set");
                            printStream.println(helpText);
                            break;
                        }
                        printStream.println("set peer-id to: " + args.get(0) + ". multichannel: " + args.get(1));
                        hubConnector.connectHub(args.get(0), Boolean.parseBoolean(args.get(1)));
                        break;
                    case CONNECT_PEER:
                        printStream.println("connecting to peer: " + args.get(0));
                        connectionListener.addMessage("Hello World!");
                        hubConnector.connectPeer(args.get(0));
                        break;
                    case HELP:
                        printStream.println(helpText);
                        break;
                    case EXIT:
                        System.exit(0);
                        break;
                    default:
                        printStream.println(helpText);
                        break;
                }
            }
        } catch (ASAPException e) {
            throw new RuntimeException(e);
        }
    }

    public static void main(String[] args) throws ASAPException, IOException, InterruptedException {
        String usageString = "optional parameters: -host [hostname] -port [portnumber] -multichannel [true/false]";
        HashMap<String, String> argumentMap = Commandline.parametersToMap(args,
                false, usageString);

        int port = DEFAULT_PORT;
        int maxIdleInSeconds = -1;

        if (argumentMap != null) {
            Set<String> keys = argumentMap.keySet();
            if (keys.contains("-help") || keys.contains("-?")) {
                System.out.println(usageString);
                System.exit(0);
            }

            // port defined
            String portString = argumentMap.get("-port");
            if (portString != null) {
                try {
                    port = Integer.parseInt(portString);
                } catch (RuntimeException re) {
                    System.err.println("port number must be a numeric: " + portString);
                    System.exit(1);
                }
            }

            String host = argumentMap.get("-host");
            if (host == null) {
                System.err.println("hostname not set");
                System.exit(1);
            }

            PrintStream o = new PrintStream("log.txt");
            PrintStream console = System.out;

            // write logs into file
            System.setOut(o);

            HubConnectorCLI cli = new HubConnectorCLI(System.in, console, host, port);

            cli.startCLI();

        }
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
