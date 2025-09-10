package net.sharksystem.hub;

import net.sharksystem.SharkException;
import net.sharksystem.hub.peerside.HubConnector;
import net.sharksystem.hub.peerside.HubConnectorDescription;

import java.io.IOException;
import java.util.List;

/**
 * Interface for applications. It allows connection management with hubs.
 */
public interface HubConnectionManager extends NewHubConnectionListenerManagement {
    /**
     * The minimum time between two sync attempts between peer and hub.
     * Around a second sounds like a reasonable time.
     */
    int MINIMAL_TIME_BEFORE_NEXT_HUB_SYNC_IN_MILLIS = 1000;

    /**
     *
     * @return time when last sync with hubs happened. Is -1 if never synced
     */
    long getLastSyncTime();

    /**
     * Force a new synchronization with all connected hubs. Use this method carefully. We do not want
     * to produce unnecessary traffic.
     */
    void forceSync();

    /**
     * Connect a hub
     * @param hcd Hub description
     * @throws SharkException Can fail, e.g.
     * <ul>
     *     <li>this implementation cannot create a connection based on given description (e.g. missing classes)</li>
     *     <li>connector can be created but hub cannot be connected</li>
     * </ul>
     */
    void connectHub(HubConnectorDescription hcd) throws SharkException, IOException;

    void connectHubs(List<HubConnectorDescription> hcdList) throws SharkException, IOException;

    /**
     * Disconnect from a hub
     * @param hcd description
     * @throws SharkException no such connection or connection is still in use
     */
    void disconnectHub(HubConnectorDescription hcd) throws SharkException, IOException;

    /**
     * Disconnect from a hub
     * @param index of hub description
     * @throws SharkException no such connection or connection is still in use
     */
    void disconnectHub(int index) throws SharkException, IOException;

    /**
     * List of connected hubs
     * @return
     */
    List<HubConnectorDescription> getConnectedHubs();
    List<FailedConnectionAttempt> getFailedConnectionAttempts();

    /**
     * Produce a hub connector by its description
     * @param hcd
     * @return
     * @throws SharkException if no connection exists
     */
    HubConnector getHubConnector(HubConnectorDescription hcd) throws SharkException;

    interface FailedConnectionAttempt {
        HubConnectorDescription getHubConnectorDescription();
        long getTimeStamp();
    }
}
