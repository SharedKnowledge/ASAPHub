package net.sharksystem.hub.peerside;

import net.sharksystem.streams.StreamPair;
import net.sharksystem.hub.*;
import net.sharksystem.hub.protocol.*;
import net.sharksystem.utils.Log;

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

    ////////////////////////////////////////////////////////////////////////////////////////////////////
    //                                   mapping API - protocol engine                                //
    ////////////////////////////////////////////////////////////////////////////////////////////////////

    public void connectHub(CharSequence localPeerID) throws IOException, ASAPHubException {
        if(isConnected()) {
            throw new ASAPHubException("already connected - disconnect first");
        }

        this.localPeerID = localPeerID;
        // create hello pdu
        HubPDURegister hubPDURegister = new HubPDURegister(localPeerID);

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

//        try {
//            hubPDUUnregister.sendPDU(this.getOutputStream());
            this.sendPDU(hubPDUUnregister);
            /*
        } catch (IOException e) {
            // tell caller
            throw new ASAPHubException("cannot disconnect - not connected or in data session, try again later");
        }

             */

        // kill connector thread
        try {
            this.getConnectorThread();
            this.getConnectorThread().kill();
        }
        catch(ASAPHubException e) {
            Log.writeLog(this, this.toString(), "no connector thread running - cannot call disconnect");
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
                    if(otherRQ.peerID.toString().equalsIgnoreCase(peerID.toString())) {
                        Log.writeLog(this, this.toString(), "there is already a connect request to " + peerID);
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
        }
        catch(IOException ioe) {
            Log.writeLog(this, this.toString(), "cannot send PDU: " + ioe.getLocalizedMessage());
            return false;
        }

        return true;
    }

    protected void actionWhenBackFromDataSession() {
        // relaunch Connector thread
        this.startConnectorSession();
//        (new ConnectorThread(this, this.getInputStream())).start();
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
        synchronized (this) {
            this.peerIDs = pdu.connectedPeers;
        }
        this.notifyListenerSynced();
    }

    /////////////////////////////////////////////////////////////////////////////////////////////////////////////
    //                                       connector status changes                                          //
    /////////////////////////////////////////////////////////////////////////////////////////////////////////////

    @Override
    protected void silenceStarted() { }

    @Override
    protected void silenceEnded() { }

    @Override
    protected void dataSessionStarted(ConnectionRequest connectionRequest, StreamPair streamPair) {
        Log.writeLog(this, this.toString(), "data session started due to request: " + connectionRequest);

        // tell listener
        if(this.listener != null) {
            // make sure not to be blocked by application programmer
            new Thread(new Runnable() {
                @Override
                public void run() {
                    for(NewConnectionListener l : listener) {
                        l.notifyPeerConnected(connectionRequest.targetPeerID, streamPair);
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
