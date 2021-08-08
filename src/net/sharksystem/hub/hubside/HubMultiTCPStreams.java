package net.sharksystem.hub.hubside;

import net.sharksystem.hub.ASAPHubException;
import net.sharksystem.streams.StreamPair;

import java.io.IOException;
import java.util.Set;

// TODO - going to make tests with shared channel implementation - this implementation is really due for v1.1.!
public class HubMultiTCPStreams extends HubGenericImpl {
    @Override
    public void notifyConnectionEnded(CharSequence sourcePeerID, CharSequence targetPeerID, StreamPair connection) throws ASAPHubException {

    }

    @Override
    protected void sendConnectionRequest(CharSequence sourcePeerID, CharSequence targetPeerID, int timeout) throws ASAPHubException, IOException {

    }

    @Override
    protected void sendDisconnectRequest(CharSequence sourcePeerID, CharSequence targetPeerID) throws ASAPHubException {

    }

    @Override
    protected void createDataConnection(CharSequence sourcePeerID, CharSequence targetPeerID, int timeout) throws ASAPHubException, IOException {

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
