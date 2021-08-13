package net.sharksystem.hub.peerside;

import net.sharksystem.asap.ASAPException;
import net.sharksystem.asap.utils.ASAPSerialization;
import net.sharksystem.hub.ASAPHubException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

public class TCPHubConnectorDescription extends HubConnectorDescription {
    private final CharSequence hostName;
    private final int port;

    static TCPHubConnectorDescription createByDescription(byte[] serializedDescription)
            throws IOException, ASAPException {

        ByteArrayInputStream bais = new ByteArrayInputStream(serializedDescription);

        // type
        byte type = ASAPSerialization.readByte(bais); // can be ignored

        if(type != TCP) {
            throw new ASAPHubException("try to construct TCPHubConnection from serialized byte[] from wrong format");
        }

        // hostname
        String hostName = ASAPSerialization.readCharSequenceParameter(bais);

        // port
        int port = ASAPSerialization.readIntegerParameter(bais);

        return new TCPHubConnectorDescription(hostName, port);
    }

    public TCPHubConnectorDescription(CharSequence hostName, int port) throws IOException {
        super(TCP);

        this.hostName = hostName;
        this.port = port;
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
        ASAPSerialization.writeIntegerParameter(this.getPort(), baos);

        return baos.toByteArray();
    }

    public CharSequence getHostName() {
        return this.hostName;
    }

    public int getPort() {
        return this.port;
    }

    protected boolean isSameSpecific(HubConnectorDescription other) {
        try {
            TCPHubConnectorDescription otherTCP = (TCPHubConnectorDescription) other;
            if(!otherTCP.getHostName().toString().equalsIgnoreCase(this.getHostName().toString())) return false;
            if(otherTCP.getPort() != this.getPort()) return false;
            return true;
        }
        catch(ClassCastException e) {
            return false;
        }
    }

    public String toString() {
        return this.getHostName() + ":" + this.getPort();
    }
}
