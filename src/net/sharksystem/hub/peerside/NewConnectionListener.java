package net.sharksystem.hub.peerside;

import net.sharksystem.streams.StreamPair;

/**
 * Peer can establish a connected mediated by a hub. This listener interface is used whenever a new connection
 * was established.
 */
public interface NewConnectionListener {
    /**
     * A new connection was established
     * @param targetPeerID
     * @param streamPair i/o streams and further information of the newly established communication
     */
    void notifyPeerConnected(CharSequence targetPeerID, StreamPair streamPair);
}
