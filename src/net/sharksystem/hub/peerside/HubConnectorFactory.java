package net.sharksystem.hub.peerside;

import net.sharksystem.asap.ASAPException;
import net.sharksystem.asap.utils.ASAPSerialization;
import net.sharksystem.hub.ASAPHubException;

import java.io.ByteArrayInputStream;
import java.io.IOException;

public class HubConnectorFactory {
    public static HubConnector createHubConnector(HubConnectorDescription hcd)
            throws IOException, ASAPHubException {

        switch (hcd.getType()) {
            case HubConnectorDescription.TCP:
                TCPHubConnectorDescription tcHcd = (TCPHubConnectorDescription) hcd;
                return SharedTCPChannelConnectorPeerSide.createTCPHubConnector(tcHcd.getHostName(), tcHcd.getPort());

            default: throw new ASAPHubException("unknown hub connector protocol type: " + hcd.getType());
        }
    }

    public static HubConnectorDescription createHubConnectorByDescription(byte[] description)
            throws IOException, ASAPException {

        ByteArrayInputStream bais = new ByteArrayInputStream(description);
        byte type = ASAPSerialization.readByte(bais);

        switch(type) {
            case HubConnectorDescription.TCP:
                return TCPHubConnectorDescription.createByDescription(description);

            default: throw new ASAPHubException("unknown hub connector protocol type: " + type);
        }
    }
}
