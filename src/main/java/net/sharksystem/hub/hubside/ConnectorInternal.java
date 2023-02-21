package net.sharksystem.hub.hubside;

import net.sharksystem.hub.ASAPHubException;
import net.sharksystem.utils.streams.StreamPair;

import java.io.IOException;

public interface ConnectorInternal extends ConnectionEstablisher {
    StreamPair initDataSession(CharSequence sourcePeerID, CharSequence targetPeerID, int timeout)
            throws ASAPHubException, IOException;

    boolean canEstablishTCPConnections();

    void createNewConnection(NewConnectionCreatorListener listener,
                             CharSequence sourcePeerID, CharSequence targetPeerID,
                             int timeOutConnectionRequest, int timeOutDataConnection) throws IOException;
}
