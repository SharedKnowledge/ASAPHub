package net.sharksystem.hub.hubside;

import net.sharksystem.hub.ASAPHubException;
import net.sharksystem.streams.StreamPair;

import java.io.IOException;

public interface ConnectorInternal extends ConnectionEstablisher {
    StreamPair initDataSession(CharSequence sourcePeerID, CharSequence targetPeerID, int timeout)
            throws ASAPHubException, IOException;
}
