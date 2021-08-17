package net.sharksystem.hub.hubside;

import net.sharksystem.hub.ASAPHubException;
import net.sharksystem.utils.Log;

import java.io.IOException;

// TODO - going to make tests with shared channel implementation - this implementation is really due for v1.1.!
public class HubSingleEntityMultipleChannels extends HubSingleEntity {

    /**
     * A peer asked for a connection request to another peer. This is a single entity implementation. Remote peer
     * connector is in this process. Ask this process to provide a data connection and come back later.
     * @param sourcePeerID
     * @param targetPeerID
     * @param timeout
     * @throws ASAPHubException
     * @throws IOException
     */
    @Override
    protected void sendConnectionRequest(CharSequence sourcePeerID, CharSequence targetPeerID, int timeout)
            throws ASAPHubException, IOException {

        // ask target connector to provide a data connection

    }

    @Override
    protected void sendDisconnectRequest(CharSequence sourcePeerID, CharSequence targetPeerID)
            throws ASAPHubException {

    }

    @Override
    protected void createDataConnection(CharSequence sourcePeerID, CharSequence targetPeerID, int timeout)
            throws ASAPHubException, IOException {
        Log.writeLog(this, "create data connection on initiator side");

    }

    @Override
    protected void sendConnectionRequest(CharSequence sourcePeerID, CharSequence targetPeerID, int timeout, boolean newConnection) throws ASAPHubException, IOException {

    }
}
