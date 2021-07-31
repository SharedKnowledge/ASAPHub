package net.sharksystem.hub.peerside;

import net.sharksystem.hub.ASAPHubException;

import java.io.IOException;

public class HubConnectorFactory {
    public static HubConnector createTCPHubConnector(CharSequence hostName, int port)
            throws IOException, ASAPHubException {

        return SharedTCPChannelConnectorPeerSide.createTCPHubConnector(hostName, port);
    }
}
