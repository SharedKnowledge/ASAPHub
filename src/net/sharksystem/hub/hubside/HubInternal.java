package net.sharksystem.hub.hubside;

import java.util.Set;

public interface HubInternal {
    /**
     * register new peer
     * @param peerId alias for peer connection
     */
    public void register(CharSequence peerId);

    /**
     * unregister a peer
     * @param peerId alias for peer connection
     */
    public void unregister(CharSequence peerId);

    /**
     * get all registered peers in hub
     * @return Set<CharSequence> with all registered peers
     */
    Set<CharSequence> getRegisteredPeers();

    /**
     * establish connection to a peer
     * @param peerId alias of peer to connect
     * @return Session object to access input- and output streams
     */
    public Session connectToPeer(CharSequence peerId);

    /**
     * close active connection from peer
     * @param peerId alias of connection
     */
    public void disconnectFromPeer(CharSequence peerId);
}
