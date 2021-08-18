package net.sharksystem.hub.hubside;

import net.sharksystem.hub.ASAPHubException;
import net.sharksystem.streams.StreamPair;
import net.sharksystem.utils.Log;

import java.io.IOException;

/**
 * Implements hub as a single entity to which connectors can connect.
 */
public class HubSingleEntitySharedChannel extends HubSingleEntity implements NewConnectionCreatorListener {
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
        Log.writeLog(this, "got connector for " + targetPeerID);

        // asked connector to establish a connection - it will try and call hub back and asks for a data session
        Log.writeLog(this, "create connection request ("  + sourcePeerID + " -> " + targetPeerID + ")");
        targetConnector.connectionRequest(sourcePeerID, targetPeerID, timeout);
    }

    protected void sendConnectionRequest(CharSequence sourcePeerID, CharSequence targetPeerID, int timeout,
                                         boolean newConnection)
            throws ASAPHubException, IOException {

        ConnectorInternal targetConnector = this.getConnector(targetPeerID);
        // an exception would have been thrown in case there is no such connector
        Log.writeLog(this, "got connector for " + targetPeerID);

        // asked connector to establish a connection - it will try and call hub back and asks for a data session
        Log.writeLog(this, "create connection request ("  + sourcePeerID + " -> " + targetPeerID + ")");
        targetConnector.connectionRequest(sourcePeerID, targetPeerID, timeout, newConnection);
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
    protected void createDataConnection(CharSequence sourcePeerID, CharSequence targetPeerID, int timeout)
            throws ASAPHubException, IOException {

        Log.writeLog(this, "createDataConnection");
        ConnectorInternal targetConnector = this.getConnector(targetPeerID);
        if(targetConnector.canEstablishTCPConnections()) {
            targetConnector.createNewConnection(this, sourcePeerID, targetPeerID, timeout, timeout);
        } else {
            Log.writeLog(this, "found connector to " + targetPeerID);
            // ask for data connection - can fail and produce exceptions
            StreamPair streamPair = targetConnector.initDataSession(sourcePeerID, targetPeerID, timeout);
            Log.writeLog(this, "got data connection (stream pair) " + targetPeerID);
            this.connectionCreated(sourcePeerID, targetPeerID, streamPair);
        }
    }

    @Override
    public void newConnectionCreated(CharSequence sourcePeerID, CharSequence targetPeerID, StreamPair streamPair) {
        this.connectionCreated(sourcePeerID, targetPeerID, streamPair);
    }
}
