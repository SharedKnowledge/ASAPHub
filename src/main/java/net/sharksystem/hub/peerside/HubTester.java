package net.sharksystem.hub.peerside;

import net.sharksystem.SharkException;
import net.sharksystem.asap.*;
import net.sharksystem.asap.apps.testsupport.ASAPTestPeerFS;
import net.sharksystem.hub.HubConnectionManager;
import net.sharksystem.hub.HubConnectionManagerImpl;
import net.sharksystem.utils.fs.FSUtils;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintStream;
import java.util.*;

public class HubTester {

    private final HubConnectionManager hubConnectionManager;
    private final ASAPPeerFS asapPeer;
    private final CharSequence format;
    private PrintStream printStream;

    public HubTester(String peerId, CharSequence format) throws IOException, ASAPException {
        String ROOT_FOLDER = "./testPeerFS" + "/" + peerId;
        FSUtils.removeFolder(ROOT_FOLDER);
        this.asapPeer = new ASAPTestPeerFS(peerId, new ArrayList<>(Collections.singletonList(format)));
        this.format = format;
        this.hubConnectionManager = new HubConnectionManagerImpl(new ASAPEncounterManagerImpl(asapPeer), asapPeer);
        printStream = System.out;
    }

    public void redirectASAPLogs(String fileName) throws FileNotFoundException {
        printStream = System.out;
        PrintStream o = new PrintStream(fileName);
        // write logs into file
        System.setOut(o);
    }


    public void connectHub(HubConnectorDescription hubConnectorDescription) throws SharkException, IOException {
        hubConnectionManager.connectHub(hubConnectorDescription);
    }

    public void sendMessages(String message, CharSequence uri, int messageCount, int delay) throws InterruptedException, ASAPException {
        for (int i = 0; i < messageCount; i++) {
            asapPeer.sendASAPMessage(this.format, uri, String.format("%s_%03d", message, i).getBytes());
            Thread.sleep(delay);
        }
    }

    public void receiveMessages() {
        asapPeer.addASAPMessageReceivedListener(this.format, new CustomMessageReceivedListener((String) asapPeer.getPeerID(), printStream));
    }

    public void shutDown(HubConnectorDescription hubConnectorDescription) throws SharkException, IOException {
        this.hubConnectionManager.disconnectHub(hubConnectorDescription);
    }

    static class CustomMessageReceivedListener implements ASAPMessageReceivedListener {
        private final String peerName;
        private final PrintStream printStream;

        CustomMessageReceivedListener(String peerName, PrintStream printStream) {
            this.peerName = peerName;
            this.printStream = printStream;
        }

        @Override
        public void asapMessagesReceived(ASAPMessages asapMessages, String senderE2E, List<ASAPHop> list) throws IOException {
            CharSequence format = asapMessages.getFormat();
            CharSequence uri = asapMessages.getURI();
            Iterator<byte[]> yourPDUIter = asapMessages.getMessages();
            printStream.printf("%s: received message from peer '%s' (%s|%s): %s %n", peerName, senderE2E, format, uri,
                    new String(getLastElement(yourPDUIter)));
        }

        public static <T> T getLastElement(Iterator<T> iterator) {
            T last = null;
            while (iterator.hasNext()) {
                last = iterator.next();
            }
            return last;
        }
    }


    public static void main(String[] args) throws IOException, SharkException, InterruptedException {
        final String helpText = "Usage: HubDemoPeer [-hV] [--multi] [--format=<format>] --host=<host> [--log=<filepath>] --peerId=<peerId> --port=<port> [--uri=<uri>] [--count=<count> --delay=<delay>\n" +
                "                   --message=<baseMessage>]\n" +
                "Connects to the ASAPHub and exchanges messages with other peers.\n" +
                "      --format=<format>   optional argument to set the format for sending and receiving messages (default: app/x-asapHubtest)\n" +
                "      --help              Show this help message and exit.\n" +
                "      --host=<host>       set host of ASAPHub\n" +
                "      --log=<filepath>    optional argument to redirect the logs of the ASAP service to a file\n" +
                "      --multiChannel [true/false]\n" +
                "      --peerId=<peerId>   set the peer-id of the ASAP peer\n" +
                "      --port=<port>       set port of ASAPHub\n" +
                "      --uri=<uri>         optional argument to set the URI of the ASAP application (default: asap://testuri)\n" +
                "  -V, --version           Print version information and exit.\n" +
                "If the HubTester is to be used for sending messages, the following argument must be passed:\n" +
                "      --send              [baseMessage] [count] [delay]";

        Map<String, List<String>> params = new HashMap<>();
        List<String> options = null;
        for (String a : args) {
            if (a.charAt(0) == '-') {
                if (a.length() < 2) {
                    System.err.println("Error at argument " + a);
                    return;
                }
                // remove hyphens
                a = a.substring(1);
                options = new ArrayList<>();
                params.put(a.substring(1), options);
            } else if (options != null) {
                options.add(a);
            } else {
                System.err.println("Illegal parameter usage");
                return;
            }
        }
        // check for help text
        if(!getMapValue(params, "help", "").isEmpty()){
            System.out.println(helpText);
            System.exit(0);
        }
        try{
            // parse required arguments
            String host = getMapValue(params, "host", null);
            int port = Integer.parseInt(getMapValue(params, "port", null));
            String peerId = getMapValue(params, "peerId", null);
            boolean multiChannel = Boolean.parseBoolean(getMapValue(params, "multiChannel", null));

            // parse optional arguments
            String format = getMapValue(params, "format", "app/x-asapHubtest");
            String uri = getMapValue(params, "uri", "asap://testuri");
            String logRedirectPath = getMapValue(params, "log", "");

            // parse optional argument group
            List<String> sendArgsList = params.get("send");
            int count = 0;
            int delay = 0;
            String message = "";
            if (sendArgsList != null) {
                if (sendArgsList.size() != 3) {
                    throw new IllegalArgumentException("not enough options were provided for the argument 'send'");
                }
                message = sendArgsList.get(0);
                count = Integer.parseInt(sendArgsList.get(1));
                delay = Integer.parseInt(sendArgsList.get(2));
            }

            FSUtils.removeFolder(String.format("./testPeerFS_%s", peerId));
            HubConnectorDescription hubDescription =
                    new TCPHubConnectorDescriptionImpl(host, port, multiChannel);
            HubTester hubTester = new HubTester(peerId, format);
            if (!logRedirectPath.isEmpty()) {
                hubTester.redirectASAPLogs(logRedirectPath);
            }
            hubTester.connectHub(hubDescription);
            if (sendArgsList != null) {
                hubTester.sendMessages(message, uri, count, delay);
            } else {
                hubTester.receiveMessages();
            }
        }catch (IllegalArgumentException | NoSuchElementException e){
            System.err.println(e.getLocalizedMessage());
            System.out.println(helpText);
        }
    }

    private static String getMapValue(Map<String, List<String>> argsMap, String key, String defaultValue) {
        if (argsMap.get(key) != null) {
            return argsMap.get(key).get(0);
        } else if (defaultValue != null) {
            return defaultValue;
        }
        throw new NoSuchElementException(String.format("required argument '%s' is missing", key));
    }

}
