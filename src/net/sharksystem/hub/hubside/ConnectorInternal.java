package net.sharksystem.hub.hubside;

import net.sharksystem.hub.ASAPHubException;
import net.sharksystem.hub.StreamPair;

import java.io.IOException;

public interface ConnectorInternal extends ConnectionEstablisher {

    StreamPair initDataSession(CharSequence targetPeerID, CharSequence sourcePeerID, int timeout) throws ASAPHubException, IOException;
}
