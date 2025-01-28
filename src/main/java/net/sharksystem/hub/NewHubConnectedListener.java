package net.sharksystem.hub;

import net.sharksystem.hub.peerside.HubConnectorDescription;

public interface NewHubConnectedListener {
    void newHubConnected(HubConnectorDescription hcd);
}
