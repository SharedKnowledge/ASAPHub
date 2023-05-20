package net.sharksystem.hub;

import net.sharksystem.SharkException;
import net.sharksystem.hub.peerside.HubConnectorDescription;

import java.io.IOException;

public interface HubConnectionManagerMessageHandler {
    void connectionChanged(HubConnectorDescription hcd, boolean connect) throws SharkException, IOException;
    void refreshHubList();
}
