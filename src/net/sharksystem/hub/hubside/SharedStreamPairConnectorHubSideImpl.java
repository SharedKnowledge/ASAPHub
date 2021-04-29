package net.sharksystem.hub.hubside;

import net.sharksystem.asap.ASAPException;
import net.sharksystem.hub.*;
import net.sharksystem.hub.protocol.HubPDU;
import net.sharksystem.hub.protocol.HubPDUChannelClear;
import net.sharksystem.hub.protocol.HubPDUHubStatusRPLY;
import net.sharksystem.hub.protocol.HubPDURegister;
import net.sharksystem.utils.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class SharedStreamPairConnectorHubSideImpl implements ConnectorInternal, AlarmClockListener, StreamPairListener {
    public static final int DEFAULT_SILENCE_TIME_OUT_IN_MILLIS = 100;
    public static final int DEFAULT_DATA_CONNECTION_TIME_OUT_IN_MILLIS = 100;
    public static final int DEFAULT_CONNECTION_REQUEST_TIME_OUT_IN_MILLIS = 100;

    private int timeOutSilenceChannel = DEFAULT_SILENCE_TIME_OUT_IN_MILLIS;
    private int timeOutDataConnection = DEFAULT_DATA_CONNECTION_TIME_OUT_IN_MILLIS;
    private int timeOutConnectionRequest = DEFAULT_CONNECTION_REQUEST_TIME_OUT_IN_MILLIS;

    private final InputStream is;
    private final OutputStream os;
    private final HubConnectorSessionProtocolEngine hubProtocolThread;
    private String peerID = null; // represented and connected peer
    private HubInternal hub = null;

    public SharedStreamPairConnectorHubSideImpl(InputStream is, OutputStream os, HubInternal hub) throws ASAPException {
        if(hub == null) throw new ASAPHubException("hub must not be null");

        this.is = is;
        this.os = os;

        if(this.is == null || this.os == null) throw new ASAPHubException("streams must not be null");

        if(hub.isRegistered(this.peerID)) {
            throw new ASAPHubException("already connected: " + this.peerID);
        }

        this.hub = hub;

        // read hello pdu
        try {
            HubPDURegister hubPDURegister = (HubPDURegister) HubPDU.readPDU(this.is);
            this.peerID = hubPDURegister.peerID.toString();
            Log.writeLog(this, "new connector: " + this.getPeerID());
            this.sendHubStatusRPLY();
        } catch (IOException e) {
            throw new ASAPHubException(e);
        }

        this.hubProtocolThread = new HubConnectorSessionProtocolEngine(this);
        this.hubProtocolThread.start();

        this.hub.register(this.peerID, this);

    }

    /////////////////////////////////////// getter
    HubInternal getHub() { return this.hub; }
    CharSequence getPeerID() { return this.peerID; }

    public OutputStream getOutputStream() {
        return this.os;
    }

    public InputStream getInputStream() {
        return this.is;
    }

    //////////////////////////////////////////////////////////////////////////////////////////////////////////
    //                                   interface to hub and connector peer side                           //
    //////////////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Method can be called from two sides - peer side. this peerID would be sourcePeerID. It could also be called
     * from inside, hub side. Peer would be target. Reaction is different a) would simply relay the request - and remember
     * it. b) would check time out and - if time enough - create a data connection
     *
     * @param sourcePeerID
     * @param targetPeerID peer ID to which a communication is to be established
     * @throws ASAPHubException another data session is already running - could try later again
     * @throws IOException
     */
    @Override
    public void connectionRequest(CharSequence sourcePeerID, CharSequence targetPeerID, int timeout)
            throws ASAPHubException, IOException {

        if(localCall(sourcePeerID, targetPeerID)) {
            // relay to hub
            this.connectionRequest(targetPeerID);
        } else {
            // remember call
            this.connectionRequestList.add(
                    new ConnectionRequest(sourcePeerID, targetPeerID, System.currentTimeMillis() + timeout));

            this.handleConnectionRequestList();
        }
    }

    void connectionRequest(CharSequence targetPeerID) throws ASAPHubException, IOException {
        this.hub.connectionRequest(this.getPeerID(), targetPeerID, this.timeOutConnectionRequest);
    }

    private List<ConnectionRequest> connectionRequestList = new ArrayList<>();

    private class ConnectionRequest {
        final CharSequence sourcePeerID;
        final CharSequence targetPeerID;
        final long until;

        ConnectionRequest(CharSequence sourcePeerID, CharSequence targetPeerID, long until) {
            this.sourcePeerID = sourcePeerID;
            this.targetPeerID = targetPeerID;
            this.until = until;
        }
    }

    synchronized private void handleConnectionRequestList() throws ASAPHubException, IOException {
        // lets see if we can start another connection
        Log.writeLog(this, "check for pending connection requests");
        if(this.statusInSilence()) {
            // we are in the right status - take oldest request
            ConnectionRequest connectionRequest = null;
            do {
                Log.writeLog(this, "#entries connection request list: " + this.connectionRequestList.size());
                connectionRequest = this.connectionRequestList.remove(0);
                if(connectionRequest.until < System.currentTimeMillis()) {
                    Log.writeLog(this, "discard connection request - timed out");
                    connectionRequest = null;
                }
            } while(connectionRequest == null && this.connectionRequestList.isEmpty());
            if(connectionRequest == null) return;

            // tell hub
            this.hub.startDataSession(this.getPeerID(), connectionRequest.targetPeerID,
                    this.initDataSessionWithPeer(connectionRequest, this.timeOutDataConnection),
                    this.timeOutDataConnection);
        } else {
            // not in silence - should we asked for silence
            if(this.statusHubConnectorProtocol()) { // we are in protocol status - change it
                this.askForSilence(this.timeOutSilenceChannel);
            }
        }
    }

    private StreamPairWrapper wrappedDataSessionStreamPair = null;
    private int sessionCounter = 0;

    synchronized private StreamPair initDataSessionWithPeer(ConnectionRequest connectionRequest, int timeout)
            throws ASAPHubException, IOException {

        if(this.wrappedDataSessionStreamPair != null) {
            Log.writeLog(this, "data session already in use");
            throw new ASAPHubException("pair is already in use");
        }
        int sessionID = this.sessionCounter++;
        Log.writeLog(this, "start new session: ");
        this.wrappedDataSessionStreamPair = new StreamPairWrapper(this.is, this.os, this, sessionID);

        // tell connector we are ready
        HubPDUChannelClear channelClear =
                new HubPDUChannelClear(this.peerID, connectionRequest.targetPeerID, timeout);

        // send channel clear pdu
        Log.writeLog(this, "send channel clear PDU: " + peerID);
        channelClear.sendPDU(this.os);

        new TimedStreamPair(this.wrappedDataSessionStreamPair, timeout);
        this.enterDataSession(this.timeOutDataConnection);

        return this.wrappedDataSessionStreamPair;
    }

    @Override
    public StreamPair initDataSession(CharSequence targetPeerID, CharSequence sourcePeerID, int timeout)
            throws ASAPHubException, IOException {

        return this.initDataSessionWithPeer(
                new ConnectionRequest(sourcePeerID, targetPeerID, System.currentTimeMillis() + timeout), timeout);
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
        if(sourcePeerID.toString().equalsIgnoreCase(this.peerID.toString())) return true;
        if(targetPeerID.toString().equalsIgnoreCase(this.peerID.toString())) return false;

        // neither
        throw new ASAPHubException("connector is neither source nor target: " + sourcePeerID + " | " + targetPeerID);
    }

    /**
     * Method can be called from two sides - peer side. this peerID would be sourcePeerID. It could also be called
     * from inside, hub side. Peer would be target. Reaction is different a) would simply relay the request - and remember
     * it. b) would kill data connection.
     *
     * @param sourcePeerID
     * @param targetPeerID peer ID to which a communication is to be established
     * @throws ASAPHubException
     */
    @Override
    public void disconnect(CharSequence sourcePeerID, CharSequence targetPeerID) throws ASAPHubException {

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

        if(this.localCall(sourcePeerID, targetPeerID))
            throw new ASAPHubException("a connection started notification cannot come from local peer");

        StreamPair stream2Peer = this.initDataSessionWithPeer(
                new ConnectionRequest(sourcePeerID, targetPeerID, System.currentTimeMillis() + timeout),
                timeout);

        // link stream pair from hub with stream pair to peer
        new StreamPairLink(stream2Peer, sourcePeerID, stream2Hub, targetPeerID);
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

    }

    /////////////////////////////////////////////////////////////////////////////////////////////////////////
    //                                         status management                                           //
    /////////////////////////////////////////////////////////////////////////////////////////////////////////
    /*
        There are several statuses
        i) Hub-Connector protocol engine is running. Wait for commands from connector/peer side
        ii) We asked peer side for silence (askedForSilence)
        iii) We are in silence mode (inSilence) - ready for data connection
        iv) We are in a data session (inDataSession) - initiated by peer side or hub
     */

    public static final int ALARM_CLOCK_ASK_SILENCE = 0;
    public static final int ALARM_CLOCK_CHANNEL_SILENCE = 1;
    public static final int ALARM_CLOCK_DATA_SESSION = 2;

    AlarmClock askedForSilenceClock = null;
    AlarmClock inSilenceClock = null;
    AlarmClock dataSessionClock = null;

    // alarm clock rings
    @Override
    public void alarmClockRinging(int yourKey) {
        Log.writeLog(this, "alarm clock is ringing...");
        switch (yourKey) {
            case ALARM_CLOCK_ASK_SILENCE:
                Log.writeLog(this, "... ended: asked for silence");
                this.askedForSilenceClock = null;
                break;

            case ALARM_CLOCK_CHANNEL_SILENCE:
                Log.writeLog(this, "... ended: channel silence");
                this.inSilenceClock = null; break;

            case ALARM_CLOCK_DATA_SESSION:
                Log.writeLog(this, "... ended: data session");
                this.dataSessionClock = null;
                this.closeDataSessionStreamPair();
                break;
        }
    }

    private boolean statusHubConnectorProtocol() {
        return !this.statusAskedForSilence() && !this.statusInSilence() && !this.statusInDataSession();
    }

    private boolean statusAskedForSilence() {
        return this.askedForSilenceClock != null;
    }

    private boolean statusInSilence() {
        return this.inSilenceClock != null;
    }

    private boolean statusInDataSession() {
        return this.dataSessionClock != null;
    }

    public void askForSilence(long waitDuration) throws IOException {
        if(askedForSilenceClock != null) this.askedForSilenceClock.kill(); // kill..
        Log.writeLog(this, "ask peer side for silence");
        this.hubProtocolThread.silenceRQ(DEFAULT_SILENCE_TIME_OUT_IN_MILLIS); // tell peer side
        this.askedForSilenceClock = new AlarmClock(waitDuration, ALARM_CLOCK_ASK_SILENCE, this); // ..and reset
        this.askedForSilenceClock.start();
    }

    public void enterSilence(long waitDuration) throws ASAPHubException, IOException {
        if(askedForSilenceClock != null) this.askedForSilenceClock.kill(); // kill - we in silence now
        this.inSilenceClock = new AlarmClock(waitDuration, ALARM_CLOCK_CHANNEL_SILENCE, this);
        this.inSilenceClock.start();
        this.handleConnectionRequestList();
    }

    public void enterDataSession(long waitDuration) {
        this.dataSessionClock = new AlarmClock(waitDuration, ALARM_CLOCK_DATA_SESSION, this);
        this.dataSessionClock.start();
        // TODO: tell peer side
    }

    private void closeDataSessionStreamPair() {
        this.wrappedDataSessionStreamPair.close();
        this.wrappedDataSessionStreamPair = null;
    }

    @Override
    public void notifyClosed(int key) {
        Log.writeLog(this, "session closed: " + key);
        this.wrappedDataSessionStreamPair = null;
    }

    @Override
    public void notifyAction(int key) {  }

    public void sessionEnded() {
        // TODO?
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
        Log.writeLog(this, "send hub status to " + this.peerID);
        hubInfoPDU.sendPDU(this.os);
    }

    /////////////////////////////////////////////////////////////////////////////////////////////////////////
    //                                           debug helper                                              //
    /////////////////////////////////////////////////////////////////////////////////////////////////////////

    public String toString() {
        return "ConnectorImpl(" + this.getPeerID() + ")";
    }
}
