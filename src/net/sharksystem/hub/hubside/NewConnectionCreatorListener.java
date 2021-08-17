package net.sharksystem.hub.hubside;

import net.sharksystem.streams.StreamPair;

interface NewConnectionCreatorListener {
    void connectionCreated(CharSequence sourcePeerID, CharSequence targetPeerID, StreamPair streamPair);
}
