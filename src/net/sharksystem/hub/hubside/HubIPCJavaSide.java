package net.sharksystem.hub.hubside;

import net.sharksystem.hub.ASAPHubException;
import net.sharksystem.hub.StreamPair;

import java.io.IOException;
import java.util.Set;

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
    public void notifyConnectionEnded(CharSequence sourcePeerID, CharSequence targetPeerID, StreamPair connection) throws ASAPHubException {

    }

    @Override
    public void register(CharSequence peerId, ConnectorInternal hubConnectorSession) {

    }

    @Override
    public void unregister(CharSequence peerId) {

    }

    @Override
    public Set<CharSequence> getRegisteredPeers() {
        return null;
    }

    @Override
    public boolean isRegistered(CharSequence peerID) {
        return false;
    }
}
