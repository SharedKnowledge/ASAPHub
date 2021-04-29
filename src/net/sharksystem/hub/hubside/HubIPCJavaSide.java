package net.sharksystem.hub.hubside;

import net.sharksystem.hub.ASAPHubException;
import net.sharksystem.hub.StreamPair;
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
import java.util.HashSet;
import java.util.Set;

public class HubIPCJavaSide extends HubGenericImpl{
    // see documentation of those abstract methods in HubGenericImpl, example implementation e.g. in HubSingleEntity
    private final String delimiter = "|";
    private final String host;
    private final int port;
    private final InputStream inputStream;
    private final OutputStream outputStream;

    public HubIPCJavaSide(String host, int port) throws IOException {
        this.host = host;
        this.port = port;
        Socket socket = new Socket(this.host,this.port);
        this.outputStream = socket.getOutputStream();
        this.inputStream = socket.getInputStream();
    }


    @Override
    protected void sendConnectionRequest(CharSequence sourcePeerID, CharSequence targetPeerID, int timeout) throws ASAPHubException, IOException {

    }

    @Override
    protected void sendDisconnectRequest(CharSequence sourcePeerID, CharSequence targetPeerID) throws ASAPHubException {

    }

    @Override
    protected void createConnection(CharSequence sourcePeerID, CharSequence targetPeerID, int timeout) throws ASAPHubException, IOException {

    }

    @Override
    public void notifyConnectionEnded(CharSequence sourcePeerID, CharSequence targetPeerID, StreamPair connection) throws ASAPHubException {

    }

    /**
     * TODO clarify whether hubConnectorSession parameter is necessary here
     * @param peerId alias for peer connection
     * @param hubConnectorSession
     */
    @Override
    public void register(CharSequence peerId, ConnectorInternal hubConnectorSession) {
        this.sendRegistrationMessage(peerId, true);
    }

    @Override
    public void unregister(CharSequence peerId) {
        this.sendRegistrationMessage(peerId, false);
    }

    private void sendRegistrationMessage(CharSequence peerId, boolean register){
        RegistrationModel peerRegistration = new RegistrationModel(new PeerModel((String) peerId), register);
        StringWriter sw = new StringWriter();
        JAXB.marshal(peerRegistration, sw);
        String xmlString = sw.toString();
        try {
            this.outputStream.write(xmlString.getBytes(StandardCharsets.UTF_8));
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
            while(true){
                String characterAsStr = String.valueOf((char) reader.read());
                if (!characterAsStr.equals(this.delimiter)){
                    message = message + characterAsStr;
                }else {
                    break;
                }
            }
            JAXBContext jaxbContext = JAXBContext.newInstance(RegisteredPeersModel.class);
            Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();

            StringReader stringReader = new StringReader(message);
            registeredPeers = (RegisteredPeersModel) unmarshaller.unmarshal(stringReader);

        } catch (IOException | JAXBException ioException) {
            ioException.printStackTrace();
        }
        Set<CharSequence> peers = new HashSet<>();
        if (null != registeredPeers.getRegisteredPeers()){
            for (PeerModel peerModel: registeredPeers.getRegisteredPeers()){
                peers.add(peerModel.getPeerId());
            }
        }
        return peers;
    }

    @Override
    public boolean isRegistered(CharSequence peerID) {
        for (CharSequence peer: this.getRegisteredPeers()) {
            if(peer.equals(peerID)){
                return true;
            }
        }
        return false;
    }

}
