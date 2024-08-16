package net.sharksystem.hub;

import net.sharksystem.SharkException;
import net.sharksystem.hub.peerside.HubConnector;
import net.sharksystem.hub.peerside.HubConnectorDescription;

import java.io.IOException;
import java.util.List;

public interface HubConnectionManager {
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
