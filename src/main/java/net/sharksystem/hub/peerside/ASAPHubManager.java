package net.sharksystem.hub.peerside;

import net.sharksystem.asap.ASAPPeer;

import java.util.Collection;

public interface ASAPHubManager {
    int DEFAULT_WAIT_INTERVAL_IN_SECONDS = 600; // 10 minutes

    /**
     * Hubs are checked frequently for new peers. Connections are established.
     *
     * @param hub
     */
    void addHub(HubConnector hub);

    void removeHub(HubConnector hub);

    void setTimeOutInMillis(int millis);

    /**
     * Callee provide a list of hub descriptions. This method tries to establish a connection.
     * Such an attempt can fail (wrong description, hub gone, network error etc.). This method can also
     * stop all connector which are no longer in the list
     *
     * @param descriptions
     * @param asapPeer
     * @param killNotDescribed if true: A complete descriptions list is assumed. Meaning:
     *                         Existing connections which are not in the list are stopped.
     */
    void connectASAPHubs(Collection<HubConnectorDescription> descriptions, ASAPPeer asapPeer, boolean killNotDescribed);

    /**
     * Hub manager is going to stop all active hub connector
     */
    void disconnectASAPHubs();

    /**
     * kill manager thread if running
     */
    void kill();
}
