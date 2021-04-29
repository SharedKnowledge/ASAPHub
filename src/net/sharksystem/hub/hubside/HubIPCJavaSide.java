package net.sharksystem.hub.hubside;

import net.sharksystem.hub.ASAPHubException;

import java.io.IOException;

public class HubIPCJavaSide extends HubGenericImpl{
    // see documentation of those abstract methods in HubGenericImpl, example implementation e.g. in HubSingleEntity

    @Override
    protected void sendConnectionRequest(CharSequence sourcePeerID, CharSequence targetPeerID, int timeout) throws ASAPHubException, IOException {

    }

    @Override
    protected void sendDisconnectRequest(CharSequence sourcePeerID, CharSequence targetPeerID) throws ASAPHubException {

    }

    @Override
    protected void createConnection(CharSequence sourcePeerID, CharSequence targetPeerID, int timeout) throws ASAPHubException, IOException {

    }

    @Override
    void notifyPeerRegistered(CharSequence peerID) {

    }

    @Override
    void notifyPeerUnregistered(CharSequence peerID) {

    }
}
