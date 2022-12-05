package net.sharksystem.hub.hubside;

import net.sharksystem.streams.StreamPair;

public interface NewConnectionCreatorListener {
    void newConnectionCreated(CharSequence sourcePeerID, CharSequence targetPeerID, StreamPair streamPair, int timeOutDataConnection);
}
