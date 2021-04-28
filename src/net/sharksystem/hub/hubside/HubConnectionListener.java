package net.sharksystem.hub.hubside;

import net.sharksystem.hub.StreamPair;

public interface HubConnectionListener {
    void connected(CharSequence peerID, StreamPair connection);
}
