package net.sharksystem.hub.hubside;

import net.sharksystem.asap.ASAPPeer;
import net.sharksystem.hub.ASAPHubException;
import net.sharksystem.hub.ConnectionPreparer;
import net.sharksystem.streams.StreamPair;
import net.sharksystem.streams.StreamPairLink;
import net.sharksystem.utils.Log;

import java.io.IOException;
import java.util.*;

public abstract class HubGenericImpl implements Hub, HubInternal {
    /**
     * Called from a connector session and asks hub to set up a new connection to target peer. This implementation
     * just asks its derived class to relay this request to the fitting part of the hub
     * @param sourcePeerID
     * @param targetPeerID peer ID to which a communication is to be established
     * @param timeout
     * @throws ASAPHubException
     * @throws IOException
     */
    @Override
    public void connectionRequest(CharSequence sourcePeerID, CharSequence targetPeerID,
                          int timeout) throws ASAPHubException, IOException {
        Log.writeLog(this, "received connection request ("
                + sourcePeerID + " -> " + targetPeerID + ")");

        // request comes from hub connector - relay this request to the other side
        this.sendConnectionRequest(sourcePeerID, targetPeerID, timeout);
    }

    /**
     * Send a connection request to hub connector target side.
     * @param sourcePeerID
     * @param targetPeerID
     * @param timeout
     */
    protected abstract void sendConnectionRequest(
            CharSequence sourcePeerID, CharSequence targetPeerID, int timeout)
                throws ASAPHubException, IOException;

    /**
     * Called from connector and asks hub to withdraw a connection request or disconnect from a peer. This implementation
     * simply calls a send method that will send this request to the appropriate part of the hub. Other implementation
     * might decide to overwrite this implementation - or ignore it all and let the timeout mechanism do the trick.
     * @param sourcePeerID
     * @param targetPeerID
     * @throws ASAPHubException
     * @see HubGenericImpl#sendDisconnectRequest(CharSequence, CharSequence)
     */
    @Override
    public void disconnect(CharSequence sourcePeerID, CharSequence targetPeerID) throws ASAPHubException {
        // request comes from hub connector - relay this request to the other side

        this.sendDisconnectRequest(sourcePeerID, targetPeerID);
    }

    /**
     * Disconnect connection - discard pending connection requests
     * @param sourcePeerID
     * @param targetPeerID
     * @throws ASAPHubException
     */
    protected abstract void sendDisconnectRequest(CharSequence sourcePeerID, CharSequence targetPeerID)
            throws ASAPHubException;

    /**
     * This call has its origin in a hub connector that re-acted on a connection request. Call start data session
     * on your local hub connector.
     * @param sourcePeerID initiator of this connection - the remote peer
     * @param targetPeerID target of this call, its hub connector is to be informed about this new connection
     * @param connection connection that is to be used for data exchange
     * @param timeout in milliseconds
     * @throws ASAPHubException
     */
    @Override
    public void startDataSession(CharSequence sourcePeerID, CharSequence targetPeerID,
                                 StreamPair connection, int timeout) throws ASAPHubException, IOException {

        ConnectionPreparer preparer = null; // should be in parameter list TODO
        // remember this request
        this.dataSessionRequestList.add(
                new DataSessionRequest(sourcePeerID, targetPeerID, connection, timeout, preparer));

        Log.writeLog(this, "start data session " + sourcePeerID + " -> " + targetPeerID);
        this.createDataConnection(sourcePeerID, targetPeerID, timeout);
    }

    private List<DataSessionRequest> dataSessionRequestList = new ArrayList<>();
    private class DataSessionRequest {
        private final long until;
        private final CharSequence sourcePeerID;
        private final CharSequence targetPeerID;
        private final StreamPair connection;
        private final int timeout;
        public ConnectionPreparer connectionPreparer;

        DataSessionRequest(CharSequence sourcePeerID, CharSequence targetPeerID,
                           StreamPair connection, int timeout) {
            this(sourcePeerID, targetPeerID, connection, timeout,null);
        }

        DataSessionRequest(CharSequence sourcePeerID, CharSequence targetPeerID,
                           StreamPair connection, int timeout, ConnectionPreparer connectionPreparer) {
            this.sourcePeerID = sourcePeerID;
            this.targetPeerID = targetPeerID;
            this.connection = connection;
            this.until = System.currentTimeMillis() + timeout;
            this.timeout = timeout;
            this.connectionPreparer = connectionPreparer;
        }
    }

    /**
     * Create a connection to this peer. Call connectionCreated on this object if done.
     * @param sourcePeerID
     * @param targetPeerID
     * @param timeout
     */
    protected abstract void createDataConnection(CharSequence sourcePeerID, CharSequence targetPeerID, int timeout)
            throws ASAPHubException, IOException;

    void connectionCreated(CharSequence sourcePeerID, CharSequence targetPeerID, StreamPair connection) {
        this.connectionCreated(sourcePeerID, targetPeerID, connection, null);
    }

    void connectionCreated(CharSequence sourcePeerID, CharSequence targetPeerID, StreamPair connection,
                           ConnectionPreparer connectionPreparer) {
        Log.writeLog(this, "connection created called");
        DataSessionRequest dataSessionRequest = null;
        List<DataSessionRequest> addAgain = new ArrayList<>();
        do {
            dataSessionRequest = this.dataSessionRequestList.remove(0);
            if(dataSessionRequest.until >= System.currentTimeMillis()) {
                // still valid
                if(!sourcePeerID.toString().equalsIgnoreCase(dataSessionRequest.sourcePeerID.toString())
                    || !targetPeerID.toString().equalsIgnoreCase(dataSessionRequest.targetPeerID.toString())) {
                    // does not match
                    Log.writeLog(this, "data session request does not fit");
                    addAgain.add(dataSessionRequest);
                    dataSessionRequest = null;
                }
            }
        } while(dataSessionRequest == null && !this.dataSessionRequestList.isEmpty());

        // re-add
        for(DataSessionRequest r : addAgain) { this.dataSessionRequestList.add(r); }

        if(dataSessionRequest != null) {
            // found match
            Log.writeLog(this, "found fitting data session in list");
            try {
                Log.writeLog(this, "create data link");
                StreamPairLink dataLink =
                        new StreamPairLink(dataSessionRequest.connection, sourcePeerID, connection, targetPeerID, false);

                /*
                if(dataSessionRequest.connectionPreparer != null)
                    dataSessionRequest.connectionPreparer.prepare(dataSessionRequest.connection);

                if(connectionPreparer != null) connectionPreparer.prepare(connection);
                 */

                // tell peers e2e is established
                Log.writeLog(this, "send ready byte to each peer");
                dataSessionRequest.connection.getOutputStream().write(ConnectionPreparer.readyByte);
                connection.getOutputStream().write(ConnectionPreparer.readyByte);

                // now go
                dataLink.start();
            } catch (IOException e) {
                Log.writeLogErr(this, "while creating stream pair link: " + e.getLocalizedMessage());
            }
        } else {
            Log.writeLog(this, "no fitting data session in list");
        }
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////////////
    //                                           Hub - external                                          //
    ///////////////////////////////////////////////////////////////////////////////////////////////////////
    @Override
    public void addASAPPeer(ASAPPeer peer) {
        // TODO
    }

    @Override
    public void removeASAPPeer(ASAPPeer peer) {
        // TODO
    }

}
