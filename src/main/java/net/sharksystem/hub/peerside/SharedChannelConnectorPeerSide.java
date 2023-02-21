package net.sharksystem.hub.peerside;

import net.sharksystem.asap.ASAPException;
import net.sharksystem.asap.utils.PeerIDHelper;
import net.sharksystem.hub.*;
import net.sharksystem.hub.protocol.*;
import net.sharksystem.utils.Log;
import net.sharksystem.utils.streams.StreamPair;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public abstract class SharedChannelConnectorPeerSide extends SharedChannelConnectorImpl implements HubConnector {
    private List<NewConnectionListener> listener = new ArrayList<>();
    private Collection<CharSequence> peerIDs = new ArrayList<>();
    private CharSequence localPeerID;
    private boolean shutdown = false;
    private HubPDUUnregister pendingDisconnectPDU = null;

    public SharedChannelConnectorPeerSide(InputStream is, OutputStream os) throws ASAPHubException {
        super(is, os);
    }

    @Override
    public CharSequence getPeerID() {
        return this.localPeerID;
    }

    @Override
    public boolean isHubSide() {
        return false;
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////
    //                                 guard methods - ensure right status                            //
    ////////////////////////////////////////////////////////////////////////////////////////////////////

    private void checkConnected() throws IOException {
        if(!this.isConnected()) throw new IOException("not connected to hub yet");
    }

    private boolean isConnected() {
        try {
            this.getConnectorThread();
            return(this.localPeerID != null);
        }
        catch (ASAPHubException e) {
            // no connector
            return false;
        }

    }

    protected void connectionLost() {
        Log.writeLog(this, "lost connection to hub permanently - should do something?");
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////
    //                                   mapping API - protocol engine                                //
    ////////////////////////////////////////////////////////////////////////////////////////////////////

    public void connectHub(CharSequence localPeerID) throws IOException, ASAPException {
        this.connectHub(localPeerID, false);
    }

    public void connectHub(CharSequence localPeerID, boolean canCreateTCPConnections)
            throws IOException, ASAPException {

        if(isConnected()) {
            throw new ASAPHubException("already connected - disconnect first");
        }

        this.localPeerID = localPeerID;
        // create hello pdu
        HubPDURegister hubPDURegister = new HubPDURegister(localPeerID, canCreateTCPConnections);

        // introduce yourself to hub
        hubPDURegister.sendPDU(this.getOutputStream());

        // start management protocol
        Log.writeLog(this, this.toString(), "start hub protocol engine");
        this.startConnectorSession();
    }

    private void startConnectorSession() {
        ConnectorThread connectorThread = new ConnectorThread(this, this.getInputStream());
        connectorThread.start();
        this.connectorSessionStarted(connectorThread);
    }

    @Override
    public void disconnectHub() throws ASAPHubException {
        // create hello pdu
        HubPDUUnregister hubPDUUnregister = new HubPDUUnregister(localPeerID);

        boolean pendingDisconnect = true;

        if(this.sendPDU(hubPDUUnregister)) {
            // kill connector thread
            try {
                this.getConnectorThread().kill();
                pendingDisconnect = false;
            }
            catch(ASAPHubException e) {
                Log.writeLog(this, this.toString(), "no connector thread running - cannot call disconnect");
            }
        }

        if(pendingDisconnect) {
            this.pendingDisconnectPDU = hubPDUUnregister;
        }
    }

    @Override
    public void syncHubInformation() throws IOException {
        // can fail ignore
        this.sendPDU(new HubPDUHubStatusRQ());
    }

    private List<HubPDUConnectPeerRQ> connectRQList = new ArrayList<>();

    @Override
    public void connectPeer(CharSequence peerID) throws IOException {
        HubPDUConnectPeerRQ connectRQ = new HubPDUConnectPeerRQ(peerID);
        if(!this.sendPDU(connectRQ)) {
            synchronized (this) {
                for(HubPDUConnectPeerRQ otherRQ : this.connectRQList) {
                    if(PeerIDHelper.sameID(peerID, otherRQ.peerID)) {
                        Log.writeLog(this, this.toString(), "already existing connect request to " + peerID);
                        return;
                    }
                }
                this.connectRQList.add(connectRQ);
            }
        }
    }

    private boolean sendPDU(HubPDU pdu)  {
        if(!this.statusHubConnectorProtocol()) return false;

        try {
            this.checkConnected();
            synchronized (this.getOutputStream()) {
                pdu.sendPDU(this.getOutputStream());
            }
            return true;
        }
        catch(IOException ioe) {
            Log.writeLog(this, this.toString(), "cannot send PDU: " + ioe.getLocalizedMessage());
            return false;
        }
    }

    protected void actionWhenBackFromDataSession() {
        Log.writeLog(this, this.toString(), "back from data session");
        if(this.pendingDisconnectPDU != null) {
            Log.writeLog(this, this.toString(), "send pending disconnect pdu");
            try {
                this.pendingDisconnectPDU.sendPDU(this.getOutputStream());
                return;
            } catch (IOException e) {
                Log.writeLog(this, this.toString(), "cannot send pending PDU: " + e.getLocalizedMessage());
            }
        }

        Log.writeLog(this, this.toString(), "restart connector session thread");
        // relaunch Connector thread if no pending disconnect or failed to send pud (for whatever reason)
        this.startConnectorSession();
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////
    //                                      listener management                                       //
    ////////////////////////////////////////////////////////////////////////////////////////////////////

    public void addListener(NewConnectionListener listener) {
        this.listener.add(listener);
    }

    public void removeListener(NewConnectionListener listener) {
        this.listener.remove(listener);
    }

    public Collection<CharSequence> getPeerIDs() throws IOException {
        synchronized (this) {
            return this.peerIDs;
        }
    }

    /////////////////////////////////////////////////////////////////////////////////////////////////////////////
    //                                       reaction on received PDUs                                         //
    /////////////////////////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void register(HubPDURegister pdu) {
        this.pduNotHandled(pdu);
    }

    @Override
    public void unregister(HubPDUUnregister pdu) {
        this.pduNotHandled(pdu);
    }

    @Override
    public void connectPeerRQ(HubPDUConnectPeerRQ pdu) {
        this.pduNotHandled(pdu);
    }

    @Override
    public void hubStatusRQ(HubPDUHubStatusRQ pdu) {
        this.pduNotHandled(pdu);
    }

    @Override
    public void hubStatusRPLY(HubPDUHubStatusRPLY pdu) {
        Log.writeLog(this, this.toString(), pdu.toString());

        Collection<CharSequence> previousIDs = this.peerIDs;
        synchronized (this) {
            this.peerIDs = pdu.connectedPeers;
        }

        // changes?
        this.notifyListenerSynced(!net.sharksystem.utils.Utils.sameContent(previousIDs, this.peerIDs));
    }

    @Override
    public void openNewTCPConnectionRequest(HubPDUConnectPeerNewTCPSocketRQ pdu) {
        this.pduNotHandled(pdu);
    }

    /////////////////////////////////////////////////////////////////////////////////////////////////////////////
    //                                       connector status changes                                          //
    /////////////////////////////////////////////////////////////////////////////////////////////////////////////

    @Override
    protected void silenceStarted() { }

    @Override
    protected void silenceEnded() { }

    @Override
    protected void dataSessionStarted(CharSequence targetPeerID, StreamPair streamPair) {
        Log.writeLog(this, this.toString(), "data session started to peer " + targetPeerID);

        Log.writeLog(this, this.toString(), "wait for ready byte " + targetPeerID);

        byte b = 0;
        while(b != Connector.readyByte) {
            Log.writeLog(this, this.toString(), "no ready byte yet");
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                // ignore
            }
            try {
                b = (byte) streamPair.getInputStream().read();
            } catch (IOException e) {
                Log.writeLog(this, this.toString(), "connection gone before usage, other peer: "
                        + targetPeerID);
                return;
            }
        }
        Log.writeLog(this, this.toString(), "got ready byte from hub - notify data session can begin");

        // tell listener
        if(this.listener != null) {
            // make sure not to be blocked by application programmer
            new Thread(new Runnable() {
                @Override
                public void run() {
                    for(NewConnectionListener l : listener) {
                        l.notifyPeerConnected(targetPeerID, streamPair);
                    }
                }
            }).start();
        }
    }

    @Override
    protected void resumedConnectorProtocol() {
        try {
            this.syncHubInformation();
        } catch (IOException e) {
            Log.writeLogErr(this, this.toString(), "sync problems: " + e.getLocalizedMessage());
        }
    }

    @Override
    protected void shutdown() {
        this.shutdown = true;
    }

    public boolean isShutdown() {
        return this.shutdown;
    }
}
