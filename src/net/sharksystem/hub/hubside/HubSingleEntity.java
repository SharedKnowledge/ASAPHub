package net.sharksystem.hub.hubside;

import net.sharksystem.hub.ASAPHubException;
import net.sharksystem.hub.StreamPair;

import java.io.IOException;

/**
 * Implements hub as a single entity to which connectors can connect.
 */
public class HubSingleEntity extends HubGenericImpl {
    /**
     * Method is called on the hub by a connector and asks for connection to another peer via its connector. In a
     * decentralized system like this - look for a registered connector and relay the request. source and target
     * switch places.
     *
     * @param sourcePeerID
     * @param targetPeerID
     * @param timeout
     * @throws ASAPHubException
     */
    @Override
    protected void sendConnectionRequest(CharSequence sourcePeerID, CharSequence targetPeerID, int timeout)
            throws ASAPHubException, IOException {

        ConnectorInternal targetConnector = this.getConnector(targetPeerID);
        // an exception would have been thrown in case there is no such connector

        // asked connector to establish a connection - it will try and call hub back and asks for a data session
        targetConnector.connectionRequest(sourcePeerID, targetPeerID, timeout);
    }

    /**
     * Method is called on the hub by a connector and asks to disconnect to another peer via its connector. In a
     * decentralized system like this - look for a registered connector and relay the request. source and target
     * switch places.
     *
     * @param sourcePeerID
     * @param targetPeerID
     * @throws ASAPHubException
     */
    @Override
    protected void sendDisconnectRequest(CharSequence sourcePeerID, CharSequence targetPeerID)
            throws ASAPHubException {

        ConnectorInternal targetConnector = this.getConnector(targetPeerID);
        // an exception would have been thrown in case there is no such connector

        targetConnector.disconnect(targetPeerID, sourcePeerID);
    }

    @Override
    protected void createConnection(CharSequence sourcePeerID, CharSequence targetPeerID, int timeout)
            throws ASAPHubException, IOException {

        ConnectorInternal targetConnector = this.getConnector(targetPeerID);
        // ask for data connection - can fail and produce exceptions
        StreamPair connection = targetConnector.initDataSession(targetPeerID, sourcePeerID, timeout);
        // tell hub about creation
        this.connectionCreated(sourcePeerID, targetPeerID, connection, timeout);
    }
}
