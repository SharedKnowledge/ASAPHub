package net.sharksystem.hub.hubside;

import net.sharksystem.hub.ASAPHubException;
import net.sharksystem.streams.StreamPair;
import net.sharksystem.streams.StreamPairImpl;
import net.sharksystem.hub.hubside.lora_ipc.*;

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
    private ConnectRequestModel activeConnection = null;


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
        this.sentConnectRequest = true;
        ConnectRequestModel connectRequest = new ConnectRequestModel(sourcePeerID.toString(), targetPeerID.toString(), timeout);
        this.sendIPCMessage(connectRequest);
    }

    @Override
    protected void sendDisconnectRequest(CharSequence sourcePeerID, CharSequence targetPeerID) throws ASAPHubException {
        if (activeConnection != null) {
            // there is no connection established yet
            if (this.activeConnection.getTargetPeerID().contentEquals(sourcePeerID) &&
                    this.activeConnection.getSourcePeerID().contentEquals(targetPeerID)) {
                // send disconnect request
                DisconnectRequestModel disconnectRequest = new DisconnectRequestModel(sourcePeerID.toString(),
                        targetPeerID.toString());
                try {
                    this.sendIPCMessage(disconnectRequest);
                } catch (IOException e) {
                    e.printStackTrace();
                }

            } else {
                System.out.println("could not send disconnect request, because current connection has another source " +
                        "and target peer id");
            }
        }else{
            System.out.println("send no disconnect request, because there is no active connection");
        }
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
        this.connectionCreated(sourcePeerID, targetPeerID, streamPair);
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
    public void register(CharSequence peerId, ConnectorInternal hubConnectorSession, boolean canCreateTCPConnections) {
        this.register(peerId, hubConnectorSession);
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

        StreamPair multihopStreamPair =
                StreamPairImpl.getStreamPairWithSessionID(socket.getInputStream(), socket.getOutputStream(),
                targetPeerID);

        this.startDataSession(sourcePeerID, targetPeerID, multihopStreamPair, timeout);
        if (!this.sentConnectRequest) {
            // only send connect request if instance was not the source of the connect request
            this.sendConnectionRequest(targetPeerID, sourcePeerID, timeout);
        }
        this.activeConnection = connectRequest;
    }

    /**
     * helper method to process an incoming disconnect request
     *
     * @param connectRequest ConnectRequestModel which contains the data
     * @throws ASAPHubException
     * @throws IOException
     */
    private void processIncomingDisconnectRequest(DisconnectRequestModel connectRequest) throws ASAPHubException, IOException {
        ConnectorInternal connectorInternal = this.connectorInternalMap.get(connectRequest.getTargetPeerID());
        CharSequence sourcePeerId = connectRequest.getSourcePeerID();
        CharSequence targetPeerId = connectRequest.getTargetPeerID();

        if(connectorInternal == null){
            System.out.println(String.format("could not close connection, because no peer with peer id %s is registered",
                   targetPeerId));
        }else{
            connectorInternal.disconnect(targetPeerId, sourcePeerId);
            this.activeConnection = null;
        }
    }

    /**
     * check whether there is an active connection to another peer
     * @return true if connected to another peer, else false
     */
    public boolean hasActiveConnection(){
        return this.activeConnection != null;
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
                        else if (receivedModel instanceof ConnectRequestModel) {
                            System.out.println("got connect request from python side");
                            this.processIncomingConnectRequest((ConnectRequestModel) receivedModel);
                        }else if (receivedModel instanceof DisconnectRequestModel) {
                            System.out.println("got disconnect request from python side");
                            this.processIncomingDisconnectRequest((DisconnectRequestModel) receivedModel);
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