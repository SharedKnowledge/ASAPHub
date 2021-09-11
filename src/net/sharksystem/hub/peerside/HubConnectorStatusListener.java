package net.sharksystem.hub.peerside;

import net.sharksystem.hub.Connector;

public interface HubConnectorStatusListener {
    /**
     * Called if an open connection is established to the hub.
     */
    void notifyConnectedAndOpen();

    /**
     * Got a sync reply (from hub)
     * @param connector
     * @param changed peer list changed - or not
     */
    void notifySynced(Connector connector, boolean changed);
}
