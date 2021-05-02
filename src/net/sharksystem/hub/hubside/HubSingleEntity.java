package net.sharksystem.hub.hubside;

import net.sharksystem.hub.ASAPHubException;
import net.sharksystem.hub.StreamPair;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

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
        StreamPair streamPair = targetConnector.initDataSession(targetPeerID, sourcePeerID, timeout);
    }

    protected ConnectorInternal getConnector(CharSequence peerID) throws ASAPHubException {
        ConnectorInternal connector = this.hubSessions.get(peerID);
        if(connector == null) throw new ASAPHubException("not connector for " + peerID);
        return connector;
    }

    /**
     * A data session came to end end. This method can called as a result of a break down in the hub, loss of connection
     * with hub connector. There is actually no need to call it - a broken data connection will result sooner or later
     * in an IOException.
     * @param sourcePeerID
     * @param targetPeerID
     * @param connection
     * @throws ASAPHubException
     */
    @Override
    public void notifyConnectionEnded(CharSequence sourcePeerID, CharSequence targetPeerID, StreamPair connection)
            throws ASAPHubException {
        this.getConnector(targetPeerID).notifyConnectionEnded(sourcePeerID, targetPeerID, connection);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////////////////
    //                                           Hub - internal                                          //
    ///////////////////////////////////////////////////////////////////////////////////////////////////////
    private Map<CharSequence, ConnectorInternal> hubSessions = new HashMap<>();

    @Override
    public boolean isRegistered(CharSequence peerID) {
        return this.hubSessions.keySet().contains(peerID);
    }

    @Override
    public Set<CharSequence> getRegisteredPeers() {
        return this.hubSessions.keySet();
    }

    @Override
    public void register(CharSequence peerID, ConnectorInternal hubConnectorSession) {
        this.hubSessions.put(peerID, hubConnectorSession);
    }

    @Override
    public void unregister(CharSequence peerID) {
        this.hubSessions.remove(peerID);
    }
}
