package net.sharksystem.hub.hubside;

import net.sharksystem.hub.ASAPHubException;
import net.sharksystem.hub.StreamPair;
import net.sharksystem.hub.hubside.lora_ipc.ConnectRequestModel;
import net.sharksystem.hub.hubside.lora_ipc.PeerModel;
import net.sharksystem.hub.hubside.lora_ipc.RegisteredPeersModel;
import net.sharksystem.hub.hubside.lora_ipc.RegistrationModel;

import javax.xml.bind.JAXB;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class HubIPCJavaSide extends HubGenericImpl {
    // see documentation of those abstract methods in HubGenericImpl, example implementation e.g. in HubSingleEntity
    private final String delimiter = "|";
    private final InputStream inputStream;
    private final OutputStream outputStream;
    private Map<CharSequence, ConnectorInternal> connectorInternalMap;

    public HubIPCJavaSide(String host, int port) throws IOException {
        Socket socket = new Socket(host, port);
        this.outputStream = socket.getOutputStream();
        this.inputStream = socket.getInputStream();
        this.connectorInternalMap = new HashMap<>();
    }


    @Override
    protected void sendConnectionRequest(CharSequence sourcePeerID, CharSequence targetPeerID, int timeout) throws ASAPHubException, IOException {
    }

    @Override
    protected void sendDisconnectRequest(CharSequence sourcePeerID, CharSequence targetPeerID) throws ASAPHubException {

    }

    @Override
    public void createDataConnection(CharSequence sourcePeerID, CharSequence targetPeerID, int timeout) throws ASAPHubException, IOException {
        this.sendXMLObject(new ConnectRequestModel(sourcePeerID.toString(), targetPeerID.toString(), timeout));

    }

    @Override
    public void notifyConnectionEnded(CharSequence sourcePeerID, CharSequence targetPeerID, StreamPair connection) throws ASAPHubException {

    }

    /**
     * @param peerId              alias for peer connection
     * @param hubConnectorSession
     */
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

    private void sendRegistrationMessage(CharSequence peerId, boolean register) {
        try {
            this.sendXMLObject(new RegistrationModel(new PeerModel((String) peerId), register));
        } catch (IOException ioException) {
            ioException.printStackTrace();
        }
    }

    @Override
    public Set<CharSequence> getRegisteredPeers() {
        RegisteredPeersModel registeredPeers = null;
        try {
            this.outputStream.write("registeredPeers?".getBytes(StandardCharsets.UTF_8));
            BufferedReader reader = new BufferedReader(new InputStreamReader(this.inputStream, StandardCharsets.UTF_8));
            String message = "";
            while (true) {
                String characterAsStr = String.valueOf((char) reader.read());
                if (!characterAsStr.equals(this.delimiter)) {
                    message = message + characterAsStr;
                } else {
                    break;
                }
            }
            registeredPeers = (RegisteredPeersModel) this.loadFromXML(message, RegisteredPeersModel.class);

        } catch (IOException ioException) {
            ioException.printStackTrace();
        }
        Set<CharSequence> peers = new HashSet<>();
        if (null != registeredPeers.getRegisteredPeers()) {
            for (PeerModel peerModel : registeredPeers.getRegisteredPeers()) {
                peers.add(peerModel.getPeerId());
            }
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

    private void sendXMLObject(Object object) throws IOException {
        StringWriter sw = new StringWriter();
        JAXB.marshal(object, sw);
        this.outputStream.write(sw.toString().getBytes(StandardCharsets.UTF_8));

    }

    private Object loadFromXML(String xml, Class<? extends Object> classOfObject) {
        try {
            Unmarshaller unmarshaller = JAXBContext.newInstance(classOfObject).createUnmarshaller();
            return unmarshaller.unmarshal(new StringReader(xml));
        } catch (JAXBException e) {
            e.printStackTrace();
        }
        return null;
    }

}
