package net.sharksystem.hub.peerside;

public interface HubConnectorStatusListener {
    /**
     * Called if an open connection is established to the hub.
     */
    void notifyConnectedAndOpen();

    /**
     * Got a sync reply (from hub)
     */
    void notifySynced();
}
