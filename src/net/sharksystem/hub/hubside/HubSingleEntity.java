package net.sharksystem.hub.hubside;

import net.sharksystem.hub.ASAPHubException;
import net.sharksystem.streams.StreamPair;
import net.sharksystem.utils.Log;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public abstract class HubSingleEntity extends HubGenericImpl {
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
        Log.writeLog(this, "new peer registered - now: " + this.hubSessions.values());
    }

    @Override
    public void unregister(CharSequence peerID) {
        this.hubSessions.remove(peerID);
        Log.writeLog(this, "new peer unregistered - now: " + this.hubSessions.values());
    }


    /*
    protected ConnectorInternal getConnectorInternal(CharSequence peerID) {
        return this.hubSessions.get(peerID);
    }
     */

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
}
