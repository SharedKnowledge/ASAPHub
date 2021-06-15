package net.sharksystem.hub.hubside;

import net.sharksystem.hub.ASAPHubException;
import net.sharksystem.hub.StreamPair;
import net.sharksystem.hub.StreamPairImpl;
import net.sharksystem.hub.hubside.lora_ipc.ConnectRequestModel;
import net.sharksystem.hub.hubside.lora_ipc.IPCModel;
import net.sharksystem.hub.hubside.lora_ipc.RegisteredPeersModel;
import net.sharksystem.hub.hubside.lora_ipc.RegistrationModel;
import net.sharksystem.utils.Log;

import java.io.*;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class HubIPCJavaSide extends HubGenericImpl {
    // see documentation of those abstract methods in HubGenericImpl, example implementation e.g. in HubSingleEntity
    private final String delimiter = "|";
    private final InputStream inputStream;
    private final OutputStream outputStream;
    private Map<CharSequence, ConnectorInternal> connectorInternalMap;
    private RegisteredPeersModel registeredPeersResponse;
    private final int messagePort;
    private final String host;
    private boolean keepIPCConnectionOpen = false;
    private Thread readingThread;
    private Socket socket;
    private boolean sentConnectRequest = false;


    public HubIPCJavaSide(String host, int port, int messagePort) throws IOException {
        this.socket = new Socket(host, port);
        this.outputStream = new DataOutputStream(socket.getOutputStream());
        this.inputStream = socket.getInputStream();
        this.connectorInternalMap = new HashMap<>();
        this.host = host;
        this.messagePort = messagePort;
    }


    @Override
    protected void sendConnectionRequest(CharSequence sourcePeerID, CharSequence targetPeerID, int timeout) throws ASAPHubException, IOException {
        ConnectRequestModel connectRequest = new ConnectRequestModel(sourcePeerID.toString(), targetPeerID.toString(), timeout);
        this.sendIPCMessage(connectRequest);
    }

    @Override
    protected void sendDisconnectRequest(CharSequence sourcePeerID, CharSequence targetPeerID) throws ASAPHubException {
//        if (establishedConnection == null) {
//            if (this.sentConnectRequest != null) {
//                if (this.sentConnectRequest .getSourcePeerID().contentEquals(sourcePeerID) && this.sentConnectRequest .getTargetPeerID().contentEquals(targetPeerID)) {
//                    this.ignoreConnectRequest = sentConnectRequest;
//                    this.sentConnectRequest = null;
//                }
//            }else{
//                System.err.println("could not withdraw connect request, because no connect request with passed parameter was sent");
//            }
//        }
//        else if(this.establishedConnection.getSourcePeerID().contentEquals(sourcePeerID) && this.establishedConnection.getTargetPeerID().contentEquals(targetPeerID)){
//            // TODO create disconnect request model and send it to python side
//        }else {
//            System.out.println("current connection has another source and target peer id");
//        }
    }

    @Override
    public void createDataConnection(CharSequence sourcePeerID, CharSequence targetPeerID, int timeout) throws ASAPHubException, IOException {
        System.out.println("call createDataConnection");
        ConnectorInternal connectorInternal = this.connectorInternalMap.get(targetPeerID);
        StreamPair streamPair = connectorInternal.initDataSession(sourcePeerID, targetPeerID, timeout);
        this.connectionCreated(sourcePeerID, targetPeerID, streamPair, timeout);
    }

    @Override
    public void notifyConnectionEnded(CharSequence sourcePeerID, CharSequence targetPeerID, StreamPair connection) throws ASAPHubException {
        this.connectorInternalMap.get(targetPeerID).notifyConnectionEnded(sourcePeerID, targetPeerID, connection);
    }

    @Override
    public void register(CharSequence peerId, ConnectorInternal hubConnectorSession) {
        this.sendRegistrationMessage(peerId, true);
        this.connectorInternalMap.put(peerId, hubConnectorSession);
    }

    @Override
    public void unregister(CharSequence peerId) {
        this.sendRegistrationMessage(peerId, false);
        this.connectorInternalMap.remove(peerId);
    }

    /**
     * helper method to register/unregister a peer
     *
     * @param peerId   peer id of peer to register/unregister
     * @param register if true a new peer will be registered, else the peer is unregistered
     */
    private void sendRegistrationMessage(CharSequence peerId, boolean register) {
        try {
            this.sendIPCMessage(new RegistrationModel((String) peerId, register));
        } catch (IOException ioException) {
            ioException.printStackTrace();
        }
    }

    @Override
    public Set<CharSequence> getRegisteredPeers() {
        RegisteredPeersModel registeredPeers;
        int attempts = 0;
        try {
            this.outputStream.write(("registeredPeers?" + this.delimiter).getBytes(StandardCharsets.UTF_8));
        } catch (IOException ioException) {
            ioException.printStackTrace();
        }
        while (this.registeredPeersResponse == null && attempts < 30) {
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            attempts++;
        }
        registeredPeers = this.registeredPeersResponse;
        Set<CharSequence> peers = new HashSet<>();
        if (null != registeredPeers.getRegisteredPeers()) {
            peers.addAll(registeredPeers.getRegisteredPeers());
        }
        return peers;
    }

    @Override
    public boolean isRegistered(CharSequence peerID) {
        for (CharSequence peer : this.getRegisteredPeers()) {
            if (peer.equals(peerID)) {
                return true;
            }
        }
        return false;
    }

    /**
     * creates an XML String from a given object and sends it via IPC to Python application
     *
     * @param ipcModel model object which should be sent to python side
     * @throws IOException if an error occurs while sending
     */
    private void sendIPCMessage(IPCModel ipcModel) throws IOException {
        this.outputStream.write((ipcModel.getIPCMessage() + this.delimiter).getBytes(StandardCharsets.UTF_8));
    }

    /**
     * helper method to process an incoming connect request
     *
     * @param connectRequest ConnectRequestModel which contains the data
     * @throws ASAPHubException
     * @throws IOException
     */
    private void processIncomingConnectRequest(ConnectRequestModel connectRequest) throws ASAPHubException, IOException {
        CharSequence sourcePeerID = connectRequest.getSourcePeerID();
        CharSequence targetPeerID = connectRequest.getTargetPeerID();
        int timeout = connectRequest.getTimeout();
        Socket socket = new Socket(this.host, this.messagePort);

        StreamPair multihopStreamPair = new StreamPairImpl(socket.getInputStream(), socket.getOutputStream(),
                targetPeerID);
        this.startDataSession(sourcePeerID, targetPeerID, multihopStreamPair, timeout);
        if(!this.sentConnectRequest){
            // only send connect request if instance was not the source of the connect request
            this.sendConnectionRequest(targetPeerID, sourcePeerID, timeout);
        }
        try {
            Thread.sleep(10000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void connectionRequest(CharSequence sourcePeerID, CharSequence targetPeerID, int timeout)
            throws ASAPHubException, IOException {
        Log.writeLog(this, "received connection request (" + sourcePeerID + " -> " + targetPeerID + ")");
        // request comes from hub connector - relay this request to the other side
        this.sendConnectionRequest(sourcePeerID, targetPeerID, timeout);
        this.sentConnectRequest = true;
    }

    /**
     * Starts new Thread, which receives IPC messages from python side.
     * Calls also appropriate method to process incoming message.
     */
    public void startReadingThread() {
        this.keepIPCConnectionOpen = true;
        Runnable r = () -> {
            try {
                while (this.keepIPCConnectionOpen) {
                    String message = readMessageFromInputStream();
                    IPCModel receivedModel = IPCModel.createModelObjectFromIPCString(message);

                    if (message != null) {
                        if (receivedModel instanceof RegisteredPeersModel) {
                            registeredPeersResponse = (RegisteredPeersModel) receivedModel;
                        } else if (receivedModel instanceof ConnectRequestModel) {
                            System.out.println("got connect request from python side");
                            this.processIncomingConnectRequest((ConnectRequestModel) receivedModel);
                        }
                    }
                }
            } catch (SocketException e) {

            } catch (IOException | ASAPHubException e) {
                e.printStackTrace();
            }
        };
        this.readingThread = new Thread(r);
        this.readingThread.start();
    }

    /**
     * read message from Python IPC-InputStream until first delimiter.
     *
     * @return message as String
     * @throws IOException
     */
    private String readMessageFromInputStream() throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(this.inputStream, StandardCharsets.UTF_8));
        String message = "";
        while (keepIPCConnectionOpen) {
            String characterAsStr = String.valueOf((char) reader.read());
            if (!characterAsStr.equals(this.delimiter)) {
                message = message + characterAsStr;
            } else {
                break;
            }
        }
        return message;
    }

    public void closeIPCConnection() throws InterruptedException, IOException {
        this.socket.close();

        this.keepIPCConnectionOpen = false;
        this.readingThread.join();
    }

}