package net.sharksystem.hub;

import net.sharksystem.asap.ASAPException;
import net.sharksystem.hub.peerside.HubConnector;
import net.sharksystem.hub.peerside.NewConnectionListener;
import net.sharksystem.hub.peerside.SharedTCPChannelConnectorPeerSide;
import net.sharksystem.streams.StreamPair;
import net.sharksystem.utils.Commandline;

import java.beans.ExceptionListener;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class HubConnectorCLI {
    static final String GET_PEERS = "peers";
    static final String SET_PEER = "set-id";
    static final String CONNECT_PEER = "connect";
    static final String HELP = "help";
    static final String EXIT = "exit";
    static final String HISTORY = "history";
    private final PrintStream printStream;
    private final InputStream inputStream;
    private int port;
    static int DEFAULT_PORT = 6000;
    private String hostName;
    private CLIConnectionListener connectionListener;
    private List<String> commandHistory;

    private static String helpText =
            "list available peers:         peers\n" +
                    "set own peer-id               set-id [peer-id] [multichannel false/true]\n" +
                    "connect to peer               connect [target-peer-id]\n" +
                    "get cli guidelines            help\n" +
                    "print history                 history\n" +
                    "export history                history [file-name/path]\n" +
                    "terminate application         exit";

    /**
     * constructor for ConnectorCLI
     * @param inputStream stream for reading commands
     * @param outputStream stream which should be used for the CLI output
     * @param host host/IP of the ASAPHub
     * @param port port of the ASAPHub
     */
    public HubConnectorCLI(InputStream inputStream, OutputStream outputStream, String host, int port) {
        printStream = new PrintStream(outputStream);
        this.inputStream = inputStream;
        this.port = port;
        this.hostName = host;
        connectionListener = new CLIConnectionListener(this.printStream);
        commandHistory = new ArrayList<>();
    }

    /**
     * alternative constructor for ConnectorCLI
     * @param inputStream stream for reading commands
     * @param outputStream stream which should be used for the CLI output
     * @param host host/IP of the ASAPHub
     * @param port port of the ASAPHub
     * @param exceptionListener listener which is called if IO Exception is thrown inside HubConnector
     */
    public HubConnectorCLI(InputStream inputStream, OutputStream outputStream, String host, int port, ExceptionListener exceptionListener) {
        this(inputStream, outputStream, host, port);
        connectionListener = new CLIConnectionListener(this.printStream, exceptionListener);
    }

    public void startCLI() throws IOException, ASAPHubException, InterruptedException {
        HubConnector hubConnector = SharedTCPChannelConnectorPeerSide.createTCPHubConnector(hostName, port);
        hubConnector.addListener(connectionListener);

        try (BufferedReader in =
                     new BufferedReader(new InputStreamReader(inputStream))) {
            String line;
            printStream.println("connector CLI started");
            while ((line = in.readLine()) != null) {
                boolean commandIsValid = true;
                List<String> args = new ArrayList<>();
                // get command and argument
                String[] commandAndArg = line.split(" ");
                String command = commandAndArg[0];
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
                        boolean canCreateConnections = Boolean.parseBoolean(args.get(1));
                        printStream.printf("set peer-id to: %s. Can create connections: %s%n", args.get(0), canCreateConnections);
                        hubConnector.connectHub(args.get(0), canCreateConnections);
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
                    case HISTORY:
                        if(args.isEmpty()){
                            // print history to console
                            commandHistory.forEach(printStream::println);
                        }else {
                            // save history as file
                            saveHistoryAsFile(args.get(0));
                        }
                        break;
                    default:
                        commandIsValid = false;
                        printStream.println(helpText);
                        break;
                }
                if(commandIsValid){
                    commandHistory.add(line);
                    Thread.sleep(2000);
                }
            }
        } catch (ASAPException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * saves the command history as file
     * @param fileName file name or path
     * @throws IOException
     */
    public void saveHistoryAsFile(String fileName) throws IOException {
        Path filePath = Paths.get(fileName);
        printStream.println(filePath.toAbsolutePath());
        PrintWriter pw = new PrintWriter(Files.newOutputStream(filePath));
        for (String command : commandHistory)
            pw.println(command);
        pw.close();
    }

    public static void main(String[] args) throws ASAPException, IOException, InterruptedException {
        String usageString = "optional parameters: -host [hostname] -port [portnumber] -multichannel [true/false]";
        HashMap<String, String> argumentMap = Commandline.parametersToMap(args,
                false, usageString);

        int port = DEFAULT_PORT;

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
    private ExceptionListener exceptionListener;


    public CLIConnectionListener(PrintStream printStream) {
        this.printStream = printStream;
        messages = new ArrayList<>();
    }

    public CLIConnectionListener(PrintStream printStream, ExceptionListener exceptionListener) {
        this.printStream = printStream;
        messages = new ArrayList<>();
        this.exceptionListener = exceptionListener;
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
            e.printStackTrace();
            if (null != exceptionListener) {
                exceptionListener.exceptionThrown(e);
            }
        }
    }
}
