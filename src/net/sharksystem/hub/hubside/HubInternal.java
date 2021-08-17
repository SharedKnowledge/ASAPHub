package net.sharksystem.hub.hubside;

import net.sharksystem.hub.ASAPHubException;

import java.util.Set;

public interface HubInternal extends ConnectionEstablisher {
    /**
     * register new peer
     * @param peerId alias for peer connection
     * @param hubConnectorSession
     */
    void register(CharSequence peerId, ConnectorInternal hubConnectorSession);

    void register(CharSequence peerId, ConnectorInternal hubConnectorSession, boolean canCreateTCPConnections);

    /**
     * unregister a peer
     * @param peerId alias for peer connection
     */
    void unregister(CharSequence peerId);

    /**
     * get all registered peers in hub
     * @return Set<CharSequence> with all registered peers
     */
    Set<CharSequence> getRegisteredPeers();

    /**
     * Ask of a peer with id is already registered with this hub.
     * @param peerID
     * @return peer is already registered or not.
     */
    boolean isRegistered(CharSequence peerID);

    /**
     * ask for a new connection to a peer
     * @param peerId alias of peer to connect
     * @throws ASAPHubException no such peer, no connection at all, internal failure
     */
//    void connect(CharSequence peerId, HubConnectionListener listener) throws ASAPHubException;

    /**
     * close active connection from peer or remove pending connection requests
     * @param peerId peer id.
     */
//    void disconnect(CharSequence peerId);
}
