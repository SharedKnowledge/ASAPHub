package net.sharksystem.hub.peerside;

public interface ASAPHubManager {
    int DEFAULT_WAIT_INTERVAL_IN_SECONDS = 600; // 10 minutes
    /**
     * Hubs are checked frequently for new peers. Connections are established.
     * @param hub
     */
    void addHub(HubConnector hub);

    void removeHub(HubConnector hub);

}
