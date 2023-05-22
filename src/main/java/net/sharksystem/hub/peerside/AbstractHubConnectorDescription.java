package net.sharksystem.hub.peerside;

import net.sharksystem.asap.ASAPException;
import net.sharksystem.asap.utils.ASAPSerialization;
import net.sharksystem.hub.ASAPHubException;

import java.io.ByteArrayInputStream;
import java.io.IOException;

public abstract class AbstractHubConnectorDescription implements HubConnectorDescription {
    private final byte type;

    protected AbstractHubConnectorDescription(byte type) {
        this.type = type;
    }

    public byte getType() {
        return this.type;
    }

    public abstract String getTypeString();

    public static AbstractHubConnectorDescription createHubConnectorDescription(byte[] serializedDescription)
            throws IOException, ASAPException {

        ByteArrayInputStream bais = new ByteArrayInputStream(serializedDescription);
        byte type = ASAPSerialization.readByte(bais);

        switch(type) {
            case TCP:
                return TCPHubConnectorDescriptionImpl.createByDescription(serializedDescription);

            default: throw new ASAPHubException("unknown hub connector protocol type: " + type);
        }
    }

    public boolean isSame(AbstractHubConnectorDescription other) {
        if(other.getType() != this.getType()) return false;

        return this.isSameSpecific(other);
    }

    protected abstract boolean isSameSpecific(AbstractHubConnectorDescription other);
    public abstract byte[] serialize() throws IOException;
}
