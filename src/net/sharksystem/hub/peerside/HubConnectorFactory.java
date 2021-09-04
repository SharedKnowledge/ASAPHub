package net.sharksystem.hub.peerside;

import net.sharksystem.asap.ASAPException;
import net.sharksystem.asap.utils.ASAPSerialization;
import net.sharksystem.hub.ASAPHubException;

import java.io.ByteArrayInputStream;
import java.io.IOException;

public class HubConnectorFactory {
    public static HubConnector createHubConnector(HubConnectorDescription hcd) throws ASAPHubException, IOException {
        switch (hcd.getType()) {
            case HubConnectorDescription.TCP:
                return SharedTCPChannelConnectorPeerSide.createTCPHubConnector(
                        hcd.getHostName(), hcd.getPortNumber(), hcd.canMultiChannel());

            default: throw new ASAPHubException("unknown hub connector protocol type: " + hcd.getType());
        }
    }

    /**
     *
     * @param hcd
     * @return
     * @throws IOException
     * @throws ASAPHubException
     * @deprecated
     */
    public static HubConnector createHubConnector(AbstractHubConnectorDescription hcd)
            throws IOException, ASAPHubException {

        switch (hcd.getType()) {
            case HubConnectorDescription.TCP:
                TCPHubConnectorDescriptionImpl tcHcd = (TCPHubConnectorDescriptionImpl) hcd;
                    return SharedTCPChannelConnectorPeerSide.createTCPHubConnector(
                            tcHcd.getHostName(), tcHcd.getPortNumber(), tcHcd.canMultiChannel());

            default: throw new ASAPHubException("unknown hub connector protocol type: " + hcd.getType());
        }
    }

    public static AbstractHubConnectorDescription createHubConnectorByDescription(byte[] description)
            throws IOException, ASAPException {

        ByteArrayInputStream bais = new ByteArrayInputStream(description);
        byte type = ASAPSerialization.readByte(bais);

        switch(type) {
            case HubConnectorDescription.TCP:
                return TCPHubConnectorDescriptionImpl.createByDescription(description);

            default: throw new ASAPHubException("unknown hub connector protocol type: " + type);
        }
    }

}
