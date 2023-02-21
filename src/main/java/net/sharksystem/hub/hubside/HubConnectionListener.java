package net.sharksystem.hub.hubside;

import net.sharksystem.utils.streams.StreamPair;

public interface HubConnectionListener {
    void connected(CharSequence peerID, StreamPair connection);
}
