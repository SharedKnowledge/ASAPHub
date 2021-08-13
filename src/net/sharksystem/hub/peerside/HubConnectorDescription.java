package net.sharksystem.hub.peerside;

import net.sharksystem.asap.ASAPException;
import net.sharksystem.asap.utils.ASAPSerialization;
import net.sharksystem.hub.ASAPHubException;

import java.io.ByteArrayInputStream;
import java.io.IOException;

public abstract class HubConnectorDescription {
    public static final byte TCP = 0x00;
    private final byte type;

    protected HubConnectorDescription(byte type) {
        this.type = type;
    }

    public byte getType() {
        return this.type;
    }

    public abstract String getTypeString();

    public static HubConnectorDescription createHubConnectorDescription(byte[] serializedDescription)
            throws IOException, ASAPException {

        ByteArrayInputStream bais = new ByteArrayInputStream(serializedDescription);
        byte type = ASAPSerialization.readByte(bais);

        switch(type) {
            case TCP:
                return TCPHubConnectorDescription.createHubConnectorDescription(serializedDescription);

            default: throw new ASAPHubException("unknown hub connector protocol type: " + type);
        }
    }

    public boolean isSame(HubConnectorDescription other) {
        if(other.getType() != this.getType()) return false;

        return this.isSameSpecific(other);
    }

    protected abstract boolean isSameSpecific(HubConnectorDescription other);
    public abstract byte[] serialize() throws IOException;
}
