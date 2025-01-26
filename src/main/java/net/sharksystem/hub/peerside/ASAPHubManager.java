package net.sharksystem.hub.peerside;

import net.sharksystem.SharkException;
import net.sharksystem.asap.ASAPPeer;

import java.util.Collection;
import java.util.List;

public interface ASAPHubManager {
    int DEFAULT_WAIT_INTERVAL_IN_SECONDS = 600; // 10 minutes

    /**
     * Hubs are checked frequently for new peers. Connections are established.
     * @param hub
     */
    void addHub(HubConnector hub);

    void removeHub(HubConnector hub);

    void setTimeOutInMillis(int millis);

    /**
     * Callee provides a list of hub descriptions. This method synchronizes existing connections with this list.
     * A new connection if a hub description is in the list but no connection has been established.
     * A connection is ended if a hub description is not in the list but a connection is established
     * (only if the boolean parameter is set).
     * Nothing happens if a connection exists and the hub is still in the list.
     *
     * Such an attempt can fail (wrong description, hub gone, network error etc.).
     *
     * @param descriptions
     * @param asapPeer
     * @param killConnectionIfNotInList if true: A complete descriptions list is assumed. Meaning:
     *                         Existing connections which are not in the list are stopped.
     */
    void connectASAPHubs(Collection<HubConnectorDescription> descriptions, ASAPPeer asapPeer,
                         boolean killConnectionIfNotInList);

    /**
     * Produce a hub connector by its description
     * @param hcd
     * @return
     * @throws SharkException if no connection exists
     */
    HubConnector getHubConnector(HubConnectorDescription hcd) throws SharkException;

    /**
     * Produce a list of running connections. Note: Chronologically order can change after each call.
     * @return
     */
    List<HubConnectorDescription> getRunningConnectorDescriptions();

    /**
     * Hub manager is going to stop all active hub connector
     */
    void disconnectASAPHubs();

    /**
     * kill manager thread if running
     */
    void kill();

    /**
     * Force hub manager to sync with hubs right now
     */
    void forceSyncWithHubs();
}
