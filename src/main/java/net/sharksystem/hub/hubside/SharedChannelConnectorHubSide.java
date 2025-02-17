package net.sharksystem.hub.hubside;

import net.sharksystem.SharkNotSupportedException;
import net.sharksystem.asap.ASAPException;
import net.sharksystem.asap.utils.PeerIDHelper;
import net.sharksystem.hub.*;
import net.sharksystem.hub.protocol.*;
import net.sharksystem.utils.AlarmClockListener;
import net.sharksystem.utils.Log;
import net.sharksystem.utils.streams.StreamPair;
import net.sharksystem.utils.streams.StreamPairLink;
import net.sharksystem.utils.streams.WrappedStreamPairListener;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class SharedChannelConnectorHubSide extends SharedChannelConnectorImpl
        implements ConnectorInternal, AlarmClockListener, WrappedStreamPairListener {

    private String peerID = null; // represented and connected peer
    private final HubInternal hub;

    /////////////////////////////////////// getter
    public HubInternal getHub() { return this.hub; }
    public CharSequence getPeerID() { return this.peerID; }

    @Override
    public boolean isHubSide() {
        return true;
    }

    public SharedChannelConnectorHubSide(InputStream is, OutputStream os, HubInternal hub) throws ASAPException {
        super(is, os);

        if(hub == null) throw new ASAPHubException("hub must not be null");

        if(hub.isRegistered(this.peerID)) {
            throw new ASAPHubException("already connected: " + this.peerID);
        }

        this.hub = hub;

        /* connector thread reads...
        // read hello pdu
        try {
            HubPDURegister hubPDURegister = (HubPDURegister) HubPDU.readPDU(this.getInputStream());
            this.peerID = hubPDURegister.peerID.toString();
            Log.writeLog(this, this.toString(), "new connector: " + this.getPeerID());
            this.sendHubStatusRPLY();
        } catch (IOException e) {
            throw new ASAPHubException(e);
        }

        this.hub.register(this.peerID, this);
         */

    }

    @Override
    public void createNewConnection(NewConnectionCreatorListener listener,
                                    CharSequence sourcePeerID, CharSequence targetPeerID,
                                    int timeOutConnectionRequest, int timeOutDataConnection) throws IOException {
        throw new SharkNotSupportedException("cannot create new connections in this implementation");
    }

    //////////////////////////////////////////////////////////////////////////////////////////////////////////
    //                    interface to establish connection to hub and connector peer side                  //
    //////////////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Method can be called from two sides
     * <br/>
     * a) peer side: this peerID would be sourcePeerID. Request is delegated to the hub which should find and ask
     * peer connector for a connection.
     * <br/>
     * b)It could also be called from inside, hub side. Peer would be target. This connector tries to
     * clear the channel and establish a data connection.
     * <br/><br/>
     *
     * @param sourcePeerID
     * @param targetPeerID peer ID to which a communication is to be established
     * @throws ASAPHubException another data session is already running - could try later again
     * @throws IOException
     */
    @Override
    public void connectionRequest(CharSequence sourcePeerID, CharSequence targetPeerID,
                                  int timeout) throws ASAPHubException, IOException {

        Log.writeLog(this, this.toString(), "connection request: " + sourcePeerID + " --> " + targetPeerID);
        if(localCall(sourcePeerID, targetPeerID)) {
            Log.writeLog(this, this.toString(), "local call");
            // relay to hub
            this.connectionRequest(targetPeerID);
        } else {
            // remember call
            ConnectionRequest newConnectionRequest = new ConnectionRequest(
                    sourcePeerID, targetPeerID,System.currentTimeMillis() + timeout,
                    this.canEstablishTCPConnections());

            // check for duplicates
            ConnectionRequest duplicate = this.connectionRequestExists(newConnectionRequest);
            if(duplicate != null) {
                StringBuilder sb = new StringBuilder();
                sb.append("ignore new connection request: ");
                sb.append("new: " + newConnectionRequest);
                sb.append("pending: " + duplicate);
            } else {
                this.pendingConnectionRequests.add(newConnectionRequest);
                this.processPendingConnectionRequestList();
            }
        }
    }

    private ConnectionRequest connectionRequestExists(ConnectionRequest newConnectionRequest) {
        ConnectionRequest duplicate = null;
        for(ConnectionRequest pendingRequest : this.pendingConnectionRequests) {
            if(this.sameConnectionRequest(pendingRequest, newConnectionRequest)) {
                duplicate = pendingRequest; break;
            }
        }
        return duplicate;
    }

    private boolean sameConnectionRequest(ConnectionRequest requestA, ConnectionRequest requestB) {
        return (
                (
                    PeerIDHelper.sameID(requestA.sourcePeerID, requestB.sourcePeerID)
                    &&
                    PeerIDHelper.sameID(requestA.targetPeerID, requestB.targetPeerID)
                )
                ||
                (
                    PeerIDHelper.sameID(requestA.sourcePeerID, requestB.targetPeerID)
                    &&
                    PeerIDHelper.sameID(requestA.targetPeerID, requestB.sourcePeerID)
                )
        );
    }

    void connectionRequest(CharSequence targetPeerID) throws ASAPHubException, IOException {
        this.hub.connectionRequest(this.getPeerID(), targetPeerID, this.getTimeOutConnectionRequest());
    }

    private List<ConnectionRequest> pendingConnectionRequests = new ArrayList<>();

    /////////////////////////////////////////////////////////////////////////////////////////////////////////////
    //                                       reaction on status changes                                        //
    /////////////////////////////////////////////////////////////////////////////////////////////////////////////

    @Override
    protected void silenceStarted() {
        try {
            this.processPendingConnectionRequestList();
        } catch (ASAPHubException | IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void silenceEnded() {

    }

    @Override
    protected void dataSessionStarted(CharSequence targetPeerID, StreamPair streamPair) {
        // hub takes care of the rest
        /*
        int timeout = (int) (connectionRequest.until - System.currentTimeMillis());
        try {
            this.getHub().startDataSession(
                    connectionRequest.targetPeerID, // switch perspective now source <-> target
                    connectionRequest.sourcePeerID,
                    streamPair,
                    timeout);
        } catch (ASAPHubException e) {
            Log.writeLogErr(this, this.toString(), "cannot start data session with hub: "
                    + e.getLocalizedMessage());
        } catch (IOException e) {
            Log.writeLogErr(this, this.toString(), "cannot start data session with hub"
                    + e.getLocalizedMessage());
        }
         */
    }

    @Override
    protected void resumedConnectorProtocol() {

    }

    protected void connectionLost() {
        this.getHub().unregister(this.getPeerID());
    }

    @Override
    protected void shutdown() {
        this.hub.unregister(this.getPeerID());
    }

    synchronized private boolean processPendingConnectionRequestList() throws ASAPHubException, IOException {
        // let's see if we can start another connection
        Log.writeLog(this, this.toString(), "process pending connection request; #entries in list: "
                + this.pendingConnectionRequests.size());

        if(this.pendingConnectionRequests.size() > 0) {
            boolean first = true;
            StringBuilder sb = new StringBuilder();
            for(ConnectionRequest request : this.pendingConnectionRequests) {
                if(first) first = false;
                else sb.append("\n");
                sb.append(request.toString());
            }
            Log.writeLog(this, this.toString(), "list: \n" + sb.toString());
        }

        if(this.pendingConnectionRequests.isEmpty()) {
            Log.writeLog(this, this.toString(), "no other requests - nothing to do");
            return false; // empty  nothing to do
        }

        // remove outdated requests
        ConnectionRequest nextRequestToProcess = null;
        while(nextRequestToProcess == null && !this.pendingConnectionRequests.isEmpty()) {
            nextRequestToProcess = this.pendingConnectionRequests.remove(0);
            if(nextRequestToProcess.until < System.currentTimeMillis()) {
                Log.writeLog(this, this.toString(), "discard connection request - timed out");
                nextRequestToProcess = null;
            }
        }

        if(nextRequestToProcess == null) return false; // list empty

        if(this.canEstablishTCPConnections() && nextRequestToProcess.newConnection) {
            try {
                return this.initDataSessionOnNewConnection(nextRequestToProcess,
                        this.getTimeOutConnectionRequest(), this.getTimeOutDataConnection());
            } catch (RuntimeException e) {
                Log.writeLog(this, "not yet implemented? Go ahead and try shared channel: "
                        + e.getLocalizedMessage());

            }
        }
        Log.writeLog(this, this.toString(), "setup data connection on shared channel");
        if (this.statusInSilence()) {
            Log.writeLog(this, this.toString(), "in silence mode - ok");
            // we are in the right status - take the oldest request

            // handle connection request
            Log.writeLog(this, this.toString(), "launch data session by request: " + nextRequestToProcess);

            // init data session - this can fail if we are not in silence mode - that's ok, though
            StreamPair streamPair = null;
            try {
                streamPair = this.initDataSession(nextRequestToProcess, this.getTimeOutDataConnection());
            }
            catch(ASAPHubException e) {
                Log.writeLog(this, this.toString(), "cannot init data session yet - we can wait");
                return false;
            }

            // tell hub
            Log.writeLog(this, this.toString(), "tell hub about newly created data session: " + nextRequestToProcess);
            this.hub.startDataSession(this.getPeerID(), nextRequestToProcess.sourcePeerID,
                    streamPair, this.getTimeOutDataConnection());
        } else {
            Log.writeLog(this, this.toString(), "not in silence mode - ask for silence");
            // not in silence - should we asked for silence
            if (this.statusHubConnectorProtocol()) { // we are in protocol status - change it
                // put request back
                Log.writeLog(this, this.toString(), "put request back in pending list: "
                        + nextRequestToProcess);
                this.pendingConnectionRequests.add(nextRequestToProcess);
                try {
                    this.askForSilence(this.getTimeOutSilenceChannel());
                }
                catch(ASAPHubException ahe) {
                    Log.writeLog(this, this.toString(),
                    "cannot ask for silence; connection request remains in pending list: "
                            + ahe.getLocalizedMessage());
                }
            } else {
                Log.writeLog(this, this.toString(),
                        "cannot ask for silence .. not in connector mode - discard connection request "
                                + nextRequestToProcess);
            }
        }
        return true;
    }

    protected boolean initDataSessionOnNewConnection(
            ConnectionRequest connectionRequest, int timeOutConnectionRequest, int timeOutDataConnection)
            throws IOException {
        throw new SharkNotSupportedException("new connections are not supported in this class");
    }

    protected void actionWhenBackFromDataSession() {
        try {
            if(this.processPendingConnectionRequestList()) return; // there are pending request
            // relaunch Connector thread
        } catch (ASAPHubException | IOException e) {
            e.printStackTrace();
        }

        // no pending requests - relaunch connector thread
        (new ConnectorThread(this, this.getInputStream())).start();
    }

    /**
     * Is it a local call (peer on the other side of the connection asked for something) or a remote call relayed by the
     * hub. Can be decided: sourcePeerID == localPeerID - local call; targetPeerID = localPeerID - remote call. Neither:
     * exception.
     *
     * @param sourcePeerID
     * @param targetPeerID
     * @return
     * @throws ASAPHubException if local peer is neither source nor target
     */
    private boolean localCall(CharSequence sourcePeerID, CharSequence targetPeerID) throws ASAPHubException {
        if(PeerIDHelper.sameID(sourcePeerID, this.getPeerID())) return true;
        if(PeerIDHelper.sameID(targetPeerID, this.getPeerID())) return false;

        // neither
        throw new ASAPHubException("connector is neither source nor target: " + sourcePeerID + " | " + targetPeerID);
    }

    /**
     * Method can be called from two sides - peer side. this peerID would be sourcePeerID. It could also be called
     * from inside, hub side. Peer would be target. Reaction is different a) would simply relay the request - and remember
     * it. b) would kill data connection.
     *
     * @param sourcePeerID
     * @param targetPeerID disconnect from what?
     * @throws ASAPHubException
     */
    @Override
    public void disconnect(CharSequence sourcePeerID, CharSequence targetPeerID) throws ASAPHubException {
        Log.writeLog(this, "disconnect called");
        ConnectionRequest removeRequest = null;
        for(ConnectionRequest request : this.pendingConnectionRequests) {
            if( PeerIDHelper.sameID(sourcePeerID, request.sourcePeerID)
                && PeerIDHelper.sameID(targetPeerID, request.targetPeerID)) {

                Log.writeLog(this, this.toString(), "found connection request");
                removeRequest = request;
                break;
            }
        }

        if(removeRequest != null) this.pendingConnectionRequests.remove(removeRequest);
    }

    /**
     * Called from hub side - a connection was started - peer must be target. Silent the channel to the peer and
     * route raw data through it - launch a data session in other words.
     * @param sourcePeerID
     * @param targetPeerID
     * @param stream2Hub connection that is to be used for data exchange
     * @param timeout in milliseconds
     * @throws ASAPHubException another data session is already running - can try later.
     */
    @Override
    public void startDataSession(CharSequence sourcePeerID, CharSequence targetPeerID,
                                 StreamPair stream2Hub, int timeout) throws ASAPHubException, IOException {

        Log.writeLog(this, this.toString(), "asked to start data session");
        if(this.localCall(sourcePeerID, targetPeerID))
            throw new ASAPHubException("a connection started notification cannot come from local peer");

        try {
            StreamPair stream2Peer = this.initDataSession(sourcePeerID, targetPeerID, timeout);
            Log.writeLog(this, this.toString(), "got connection to peer side");

            // link stream pair from hub with stream pair to peer
            new StreamPairLink(stream2Peer, sourcePeerID, stream2Hub, targetPeerID);
            Log.writeLog(this, this.toString(), "created and started stream pair link");
        }
        catch(ASAPHubException ahe) {
            Log.writeLog(this, this.toString(), "could not establish data session: "
                    + ahe.getLocalizedMessage());
            Log.writeLog(this, this.toString(), "could not establish data session: ");
            ConnectionRequest pendingRequest = ConnectionRequest.createNewConnectRequest(sourcePeerID, targetPeerID);
            Log.writeLog(this, this.toString(), "REMEMBER REQUEST ?: " + pendingRequest);
        }
    }

    /**
     * Kills an open data connection.
     *
     * @param sourcePeerID
     * @param targetPeerID
     * @param connection
     * @throws ASAPHubException
     */
    @Override
    public void notifyConnectionEnded(CharSequence sourcePeerID, CharSequence targetPeerID, StreamPair connection)
            throws ASAPHubException {

        Log.writeLog(this, this.toString(), "connection ended .. nothing to do here (?)");

    }

    /////////////////////////////////////////////////////////////////////////////////////////////////////////
    //                                         protocol actions                                            //
    /////////////////////////////////////////////////////////////////////////////////////////////////////////

    private void sendHubStatusRPLY() throws IOException {
        Set<CharSequence> allPeers = this.hub.getRegisteredPeers();
        // sort out calling peer
        //Log.writeLog(this, "assemble registered peer list for " + this.peerID);
        Set<CharSequence> peersWithoutCaller = new HashSet();
        for (CharSequence peerName : allPeers) {
//            Log.writeLog(this, peerName + " checked for peer list for " + this.peerID);
            if (!peerName.toString().equalsIgnoreCase(this.peerID)) {
                peersWithoutCaller.add(peerName);
            }
        }
        HubPDU hubInfoPDU = new HubPDUHubStatusRPLY(peersWithoutCaller);
        Log.writeLog(this, this.toString(), "send hub status to " + this.peerID);
        hubInfoPDU.sendPDU(this.getOutputStream());
    }


    /////////////////////////////////////////////////////////////////////////////////////////////////////////////
    //                                       reaction on received PDUs                                         //
    /////////////////////////////////////////////////////////////////////////////////////////////////////////////

    private boolean peerSideCanCreateTCPConnections = false;

    @Override
    public void register(HubPDURegister pdu) {
        // received register pdu - tell hub
        Log.writeLog(this, this.toString(),
                "received register from peer side - tell hub (peer can create TCP = "
                        + pdu.canCreateTCPConnections + ")");
        this.peerID = pdu.peerID.toString();
        this.peerSideCanCreateTCPConnections = pdu.canCreateTCPConnections;
        try {
            this.sendHubStatusRPLY();
        } catch (IOException e) {
            Log.writeLog(this, this.toString(), "failed to send hub status" + e.getLocalizedMessage());
        }
        this.hub.register(pdu.peerID, this, pdu.canCreateTCPConnections);

        /* connector thread reads...
        // read hello pdu
        try {
            HubPDURegister hubPDURegister = (HubPDURegister) HubPDU.readPDU(this.getInputStream());
            this.peerID = hubPDURegister.peerID.toString();
            Log.writeLog(this, this.toString(), "new connector: " + this.getPeerID());
            this.sendHubStatusRPLY();
        } catch (IOException e) {
            throw new ASAPHubException(e);
        }

        this.hub.register(this.peerID, this);
         */

    }

    @Override
    public void unregister(HubPDUUnregister pdu) {
        Log.writeLog(this, this.toString(), "received unregister from peer side - tell hub");
        this.hub.unregister(pdu.peerID);
    }

    @Override
    public void openNewTCPConnectionRequest(HubPDUConnectPeerNewTCPSocketRQ hubPDU) {
        this.pduNotHandled(hubPDU);
    }

    @Override
    public void connectPeerRQ(HubPDUConnectPeerRQ pdu) {
        // received connection request from peer side - tell hub
        Log.writeLog(this, this.toString(), "received connection RQ from peer side - tell hub");
        try {
            this.hub.connectionRequest(
                    this.peerID, pdu.peerID,
                    this.getTimeOutConnectionRequest());

        } catch (ASAPHubException | IOException e) {
            Log.writeLogErr(this, this.toString(), "connection RQ failed with hub: " + e.getLocalizedMessage());
        }
    }

    @Override
    public void hubStatusRQ(HubPDUHubStatusRQ pdu) {
        Set<CharSequence> peersWithoutThis = new HashSet<>();
        peersWithoutThis.addAll(this.getHub().getRegisteredPeers());
        peersWithoutThis.remove(this.getPeerID());

        HubPDUHubStatusRPLY reply = new HubPDUHubStatusRPLY(peersWithoutThis);
        try {
            reply.sendPDU(this.getOutputStream());
        } catch (IOException e) {
            Log.writeLogErr(this, this.toString(), "cannot send hub status reply: " + e.getLocalizedMessage());
        }
    }

    @Override
    public void hubStatusRPLY(HubPDUHubStatusRPLY pdu) {
        this.pduNotHandled(pdu);
    }

    public boolean hubSideCanEstablishTCPConnections() {
        return false;
    }

    @Override
    public boolean canEstablishTCPConnections() {
        return this.hubSideCanEstablishTCPConnections() && this.peerSideCanCreateTCPConnections;
    }

    /////////////////////////////////////////////////////////////////////////////////////////////////////////
    //                                           debug helper                                              //
    /////////////////////////////////////////////////////////////////////////////////////////////////////////
}
