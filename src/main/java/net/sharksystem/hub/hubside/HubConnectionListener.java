package net.sharksystem.hub.hubside;

import net.sharksystem.streams.StreamPair;

public interface HubConnectionListener {
    void connected(CharSequence peerID, StreamPair connection);
}
