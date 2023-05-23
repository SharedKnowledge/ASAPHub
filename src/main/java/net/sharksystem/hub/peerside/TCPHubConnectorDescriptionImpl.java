package net.sharksystem.hub.peerside;

import net.sharksystem.asap.ASAPException;
import net.sharksystem.asap.ASAPHop;
import net.sharksystem.asap.utils.ASAPSerialization;
import net.sharksystem.hub.ASAPHubException;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

public class TCPHubConnectorDescriptionImpl extends AbstractHubConnectorDescription {
    private final CharSequence hostName;
    private final int port;
    private final boolean multiChannel;

    static TCPHubConnectorDescriptionImpl createByDescription(byte[] serializedDescription)
            throws IOException, ASAPException {

        ByteArrayInputStream bais = new ByteArrayInputStream(serializedDescription);
        return readDescription(bais);
    }

    public static TCPHubConnectorDescriptionImpl readDescription(InputStream is) throws ASAPException, IOException {
        byte type = ASAPSerialization.readByte(is); // can be ignored

        if(type != TCP) {
            throw new ASAPHubException("try to construct TCPHubConnection from serialized byte[] from wrong format");
        }

        // hostname
        String hostName = ASAPSerialization.readCharSequenceParameter(is);
        // port
        int port = ASAPSerialization.readIntegerParameter(is);
        // multichannel
        boolean multiChannel = ASAPSerialization.readBooleanParameter(is);

        return new TCPHubConnectorDescriptionImpl(hostName, port, multiChannel);
    }

    public TCPHubConnectorDescriptionImpl(CharSequence hostName, int port) throws IOException {
        this(hostName, port, false);
    }

    public TCPHubConnectorDescriptionImpl(CharSequence hostName, int port, boolean multiChannel) throws IOException {
        super(TCP);
        this.hostName = hostName;
        this.port = port;
        this.multiChannel = multiChannel;
    }

    public String getTypeString() {
        return "TCP";
    }

    public byte[] serialize() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        // type
        ASAPSerialization.writeByteParameter(HubConnectorDescription.TCP, baos);
        // hostname
        ASAPSerialization.writeCharSequenceParameter(this.getHostName(), baos);
        // port
        ASAPSerialization.writeIntegerParameter(this.port, baos);
        // multichannel
        ASAPSerialization.writeBooleanParameter(this.multiChannel, baos);

        return baos.toByteArray();
    }



    public CharSequence getHostName() {
        return this.hostName;
    }

    @Override
    public int getPortNumber() throws ASAPHubException {
        return this.port;
    }

    @Override
    public boolean canMultiChannel() {
        return this.multiChannel;
    }

    public boolean isSame(HubConnectorDescription hcd) {
        if(hcd.getType() != TCP) {
            return false;
        }

        try {
            if (!hcd.getHostName().toString().equalsIgnoreCase(this.hostName.toString())) {
                return false;
            }
            if (hcd.getPortNumber() != this.port) {
                return false;
            }
        }
        catch(ASAPHubException e) {
            // definitive no TCP connector description
            return false;
        }

        // no difference found
        return true;
    }

    protected boolean isSameSpecific(AbstractHubConnectorDescription other) {
        try {
            TCPHubConnectorDescriptionImpl otherTCP = (TCPHubConnectorDescriptionImpl) other;
            if(!otherTCP.getHostName().toString().equalsIgnoreCase(this.getHostName().toString())) return false;
            if(otherTCP.getPortNumber() != this.getPortNumber()) return false;
            return true;
        }
        catch(ClassCastException | ASAPHubException e) {
            return false;
        }
    }

    public String toString() {
        return this.hostName + ":" + this.port;
    }

    /**
     * Serializes a List of hubConnectorDescription objects to a byte array
     * @param hubConnectorDescriptions List of HubConnectorDescription objects
     * @return serialized List as byte array
     * @throws IOException
     */
    public static byte[] serializeConnectorDescriptionList(List<HubConnectorDescription> hubConnectorDescriptions) throws IOException{
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        if(hubConnectorDescriptions == null || hubConnectorDescriptions.isEmpty()) {
            // no hcd
            ASAPSerialization.writeIntegerParameter(0, baos);
        }else {
            // write number of descriptions
            ASAPSerialization.writeIntegerParameter(hubConnectorDescriptions.size(), baos);
            for (HubConnectorDescription hcd : hubConnectorDescriptions) {
                baos.write(hcd.serialize());
            }
        }
        return baos.toByteArray();
    }

    /**
     *
     * @param serializedHubDescriptions List HubConnectorDescription objects serialized as byte array
     * @return List containing the deserialized HubConnectorDescription objects
     * @throws IOException
     * @throws ASAPException throws an ASAPException if HubConnectorDescription isn't of type TCP-HubConnectorDescription
     */
    public static List<HubConnectorDescription> deserializeHubConnectorDescriptionList(byte[] serializedHubDescriptions) throws IOException, ASAPException {
        List<HubConnectorDescription> hubDescriptions = new ArrayList<>();
        ByteArrayInputStream is = new ByteArrayInputStream(serializedHubDescriptions);
        int number = ASAPSerialization.readIntegerParameter(is);
        while(number-- > 0) {
            hubDescriptions.add(TCPHubConnectorDescriptionImpl.readDescription(is));
        }
        return hubDescriptions;
    }

}
