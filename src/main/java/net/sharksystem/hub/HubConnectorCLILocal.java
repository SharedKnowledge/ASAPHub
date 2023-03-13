package net.sharksystem.hub;

import net.sharksystem.asap.ASAPException;
import net.sharksystem.hub.peerside.HubConnector;
import net.sharksystem.hub.peerside.NewConnectionListener;
import net.sharksystem.hub.peerside.SharedTCPChannelConnectorPeerSide;
import net.sharksystem.utils.streams.StreamPair;
import net.sharksystem.utils.Commandline;

import java.io.*;
import java.net.Socket;
import java.util.*;

public class HubConnectorCLILocal {
    static final String GET_PEERS = "peers";
    static final String CREATE_CONNECTOR = "create";
    static final String SET_PEER = "set-id";
    static final String CONNECT_PEER = "connect";
    static final String HELP = "help";
    static final String EXIT = "exit";
    private final PrintStream printStream;
    private final InputStream inputStream;
    private int port;
    static int DEFAULT_PORT = 6910;
    //    private String hostName = "localhost";
    private String hostName;
    private boolean multiChannel;

    private static String helpText =
                    "list available peers:         [connector-name]  peers\n" +
                    "set peer-id of connector      set-id [connector-name] [peer-id] [multichannel false/true]\n" +
                    "connect to peer               connect [connector-name]  [target-peer-id]\n" +
                    "get cli guidelines            help\n" +
                    "terminate application         exit" +
                    "create connector              create [connector-name]";
    private Map<String, HubConnector> connectors = new HashMap<>();


    public HubConnectorCLILocal(InputStream inputStream, OutputStream outputStream, String host, int port, boolean multiChannel) {
        printStream = new PrintStream(outputStream);
        this.inputStream = inputStream;
        this.port = port;
        this.hostName = host;
        this.multiChannel = multiChannel;
    }

    public void startCLI() throws IOException, ASAPHubException, InterruptedException {
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
                HubConnector connector;
                switch (command) {
                    case CREATE_CONNECTOR:
                        boolean canCreateConnections = Boolean.parseBoolean(args.get(1));
                        printStream.printf("create connector '%s'. Can create connections: %s%n", args.get(0), canCreateConnections);
                        connector = SharedTCPChannelConnectorPeerSide.createTCPHubConnector(hostName, port);
                        CLIConnectionListener connectionListener = new CLIConnectionListener(this.printStream); // maybe move to "CONNECT_PEER"
                        connectionListener.addMessage("Hello World!");
                        connector.addListener(connectionListener);
                        this.connectors.put(args.get(0), connector);
                        break;
                    case GET_PEERS:
                        printStream.printf("use connector '%s'.get available peers%n", args.get(0));
                        connector = this.connectors.get(args.get(0));
                        connector.syncHubInformation();
                        // wait for async reply of syncHubInformation
                        Thread.sleep(500);
                        printStream.println(connector.getPeerIDs());
                        break;
                    case SET_PEER:
                        printStream.printf("use connector '%s'. set peer-id to: %s%n",
                                args.get(0), args.get(1));
                        this.connectors.get(args.get(0)).connectHub(args.get(1));
                        break;
                    case CONNECT_PEER:
                        printStream.printf("use connector '%s'.connecting to peer: %s%n", args.get(0), args.get(1));
                        this.connectors.get(args.get(0)).connectPeer(args.get(1));
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
                Thread.sleep(2000);
            }
        } catch (ASAPException e) {
            throw new RuntimeException(e);
        }
    }

    public static void main(String[] args) throws ASAPException, IOException, InterruptedException {
        String usageString = "optional parameters: -host [hostname] -port [portnumber] -multichannel [true/false]";
        HashMap<String, String> argumentMap = Commandline.parametersToMap(args,
                false, usageString);
        String host = "localhost";
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

            String hostStr = argumentMap.get("-host");
            if (hostStr != null) {
                host = hostStr;
            }
            String multiChannelStr = argumentMap.get("-multichannel");
            boolean multiChannel = false;
            if (multiChannelStr != null) {
                multiChannel = Boolean.parseBoolean(multiChannelStr);
            }

            PrintStream o = new PrintStream("log.txt");
            PrintStream console = System.out;

            // write logs into file
//            System.setOut(o);

            HubConnectorCLILocal cli = new HubConnectorCLILocal(System.in, System.out, host, port, multiChannel);

            cli.startCLI();

        }
    }
}
