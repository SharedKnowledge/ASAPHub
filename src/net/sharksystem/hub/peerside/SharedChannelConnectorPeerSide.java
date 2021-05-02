package net.sharksystem.hub.peerside;

import net.sharksystem.asap.ASAPException;
import net.sharksystem.hub.*;
import net.sharksystem.hub.protocol.*;
import net.sharksystem.utils.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class SharedChannelConnectorPeerSide extends SharedChannelConnectorImpl implements HubConnector {
    private NewConnectionListener listener;
    private Collection<CharSequence> peerIDs = new ArrayList<>();
    private CharSequence localPeerID;

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
        (new ConnectorThread(this, this.getInputStream())).start();
    }

    @Override
    public void disconnectHub() throws ASAPHubException {
        this.getConnectorThread().kill();
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

    private boolean sendPDU(HubPDU pdu) throws IOException {
        if(!this.statusHubConnectorProtocol()) return false;

        this.checkConnected();
        synchronized (this.getOutputStream()) {
            pdu.sendPDU(this.getOutputStream());
        }

        return true;
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////
    //                                      listener management                                       //
    ////////////////////////////////////////////////////////////////////////////////////////////////////

    public void setListener(NewConnectionListener listener) {
        this.listener = listener;
    }

    public Collection<CharSequence> getPeerIDs() throws IOException {
        this.checkConnected();
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
    }

    /////////////////////////////////////////////////////////////////////////////////////////////////////////////
    //                                       connector status changes                                          //
    /////////////////////////////////////////////////////////////////////////////////////////////////////////////

    @Override
    protected void silenceStarted() { }

    @Override
    protected void silenceEnded() { }

    @Override
    protected void dataSessionStarted(TimedStreamPair timedStreamPair) { }

    @Override
    protected void dataSessionEnded() {
        try {
            this.syncHubInformation();
        } catch (IOException e) {
            Log.writeLogErr(this, this.toString(), "sync problems: " + e.getLocalizedMessage());
        }
    }
}
