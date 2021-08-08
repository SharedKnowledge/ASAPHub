package net.sharksystem.hub.hubside;

import net.sharksystem.hub.ASAPHubException;

import java.io.IOException;

// TODO - going to make tests with shared channel implementation - this implementation is really due for v1.1.!
public class HubSingleEntityMultipleChannels extends HubSingleEntity {
    @Override
    protected void sendConnectionRequest(CharSequence sourcePeerID, CharSequence targetPeerID, int timeout)
            throws ASAPHubException, IOException {

    }

    @Override
    protected void sendDisconnectRequest(CharSequence sourcePeerID, CharSequence targetPeerID)
            throws ASAPHubException {

    }

    @Override
    protected void createDataConnection(CharSequence sourcePeerID, CharSequence targetPeerID, int timeout)
            throws ASAPHubException, IOException {

    }
}
