package net.sharksystem.hub;

import net.sharksystem.asap.ASAPException;
import net.sharksystem.utils.Log;

import java.io.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class HubConnectorImpl implements HubConnector {
    private final InputStream hubIS;
    private final OutputStream hubOS;

    private NewConnectionListener listener;

    private HubConnectorProtocolEngine hubConnectorProtocolEngine;
    private DataSessionEstablishmentThread dataChannelEstablishmentThread;

    private Collection<CharSequence> peerIDs = new ArrayList<>();
    private CharSequence localPeerID;

    private long silentUntil = 0;
    private long lastMaxIdleInMillis = 1000;

    public HubConnectorImpl(InputStream hubIS, OutputStream hubOS) {
        this.hubIS = hubIS;
        this.hubOS = hubOS;
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////
    //                                 guard methods - ensure right status                            //
    ////////////////////////////////////////////////////////////////////////////////////////////////////

    private void checkConnected() throws IOException {
        if(!this.isConnected()) throw new IOException("not connected to hub yet");
    }

    private boolean isConnected() {
        return(this.localPeerID != null);
    }

    private boolean hubProtocolRunning() {
        return this.hubConnectorProtocolEngine != null;
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////
    //                                   mapping API - protocol engine                                //
    ////////////////////////////////////////////////////////////////////////////////////////////////////

    public void connectHub(CharSequence localPeerID) throws IOException {
        if(this.localPeerID != null) {
            throw new IOException("already connected - disconnect first");
        }

        this.localPeerID = localPeerID;
        // create hello pdu
        HubPDURegister hubPDURegister = new HubPDURegister(localPeerID);

        // introduce yourself to hub
        hubPDURegister.sendPDU(this.hubOS);

        // start management protocol
        Log.writeLog(this, "start hub protocol engine");
        this.hubConnectorProtocolEngine = new HubConnectorProtocolEngine(this.hubIS, this.hubOS);
        this.hubConnectorProtocolEngine.start();
    }

    @Override
    public void disconnectHub() throws IOException {
        this.localPeerID = null;
        this.hubConnectorProtocolEngine.kill();
    }

    @Override
    public void syncHubInformation() throws IOException {
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
                        Log.writeLog(this, "there is already a connect request to " + peerID);
                        return;
                    }
                }
                this.connectRQList.add(connectRQ);
            }
        }
    }

    private boolean sendPDU(HubPDU pdu) throws IOException {
        this.checkConnected();
        if(this.hubProtocolRunning()) {
            synchronized (this.hubOS) {
                pdu.sendPDU(this.hubOS);
            }
            return true;
        }

        return false;
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

    public String toString() {
        StringBuilder sb = new StringBuilder();
        if(this.isConnected()) {
            sb.append(this.localPeerID);
        } else {
            sb.append("not connected to hub");
        }

        return sb.toString();
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////
    //                                data connection establishment                                   //
    ////////////////////////////////////////////////////////////////////////////////////////////////////

    private void restartHubProtocolEngine() {
        if(!this.isConnected()) {
            // not connected any longer
            Log.writeLog(this,
                    "not connected any longer to hub - do not try to relaunch hub protocol engine: " + this);
            return;
        }

        Log.writeLog(this, "restart hub protocol engine: " + this);
        this.hubConnectorProtocolEngine = new HubConnectorProtocolEngine(this.hubIS, this.hubOS);
        this.hubConnectorProtocolEngine.start();
        Log.writeLog(this, "ask for fresh hub status: " + this);
        try {
            HubConnectorImpl.this.syncHubInformation();
        } catch (IOException e) {
            /* ignore */
        }

        // send postponed connect pdus - if any
        synchronized (this) {
            if(!this.connectRQList.isEmpty()) {
                Log.writeLog(this, "send stored hub pdus");
                for (HubPDU connectRQ : this.connectRQList) {
                    try {
                        synchronized (this.hubOS) {
                            connectRQ.sendPDU(this.hubOS);
                        }
                    } catch (IOException e) {
                        // fatal
                        Log.writeLogErr(this, "cannot write to stream / fatal: " + e.getLocalizedMessage());
                        this.hubConnectorProtocolEngine.kill();
                        return;
                    }
                }
                this.connectRQList = new ArrayList<>();
            }
        }
    }

    private class DataSessionEstablishmentThread implements Runnable {
        private final InputStream hubIS;
        private final OutputStream hubOS;
        private final long remainSilentInMillis;

        public DataSessionEstablishmentThread(InputStream hubIS, OutputStream hubOS, long remainSilentInMillis) {
            this.hubIS = hubIS;
            this.hubOS = hubOS;
            this.remainSilentInMillis = remainSilentInMillis;
            HubConnectorImpl.this.dataChannelEstablishmentThread = this;
        }

        public void run() {
            if (HubConnectorImpl.this.silentUntil >= System.currentTimeMillis()) {
                // still valid - tell hub we are ready here
                Log.writeLog(this, "send silent reply - remain silent in ms " + this.remainSilentInMillis);

                HubPDUSilentRPLY silentRPLY = new HubPDUSilentRPLY(this.remainSilentInMillis);
                try {
                    synchronized (this.hubOS) {
                        silentRPLY.sendPDU(this.hubOS);
                    }

                    HubPDU hubPDU = HubPDU.readPDU(this.hubIS);

                    // got a channel clear pdu
                    if (hubPDU instanceof HubPDUChannelClear) {
                        HubPDUChannelClear channelClear = (HubPDUChannelClear) hubPDU;
                        HubConnectorImpl.this.lastMaxIdleInMillis = channelClear.maxIdleInMillis;
                        Log.writeLog(this, "channel is clear - maxIdleInMillis == "
                                + channelClear.maxIdleInMillis);

                        // who is partner?
                        CharSequence otherPeerID =
                            channelClear.sourcePeerID.toString().equalsIgnoreCase(localPeerID.toString()) ?
                                    channelClear.targetPeerID : channelClear.sourcePeerID;

                        String debugID = "Connector => HubSession (" + localPeerID + ")";
                        BorrowedConnection newConnection = new BorrowedConnection(
                                hubIS, hubOS, debugID, channelClear.maxIdleInMillis);

                        newConnection.start();

                        // tell listener
                        listener.notifyPeerConnected(
                                new SessionConnectionImpl(
                                        newConnection.getInputStream(),
                                        newConnection.getOutputStream(),
                                        otherPeerID));

                        newConnection.join();
                        // that's it.
                        Log.writeLog(this, "data session ended");
                    } else {
                        // nonsense
                        Log.writeLog(this, "another pdu came in");
                    }
                } catch (IOException e) {
                    Log.writeLog(this, e.getLocalizedMessage());
                    // cannot recover from that - TODO
                } catch (ASAPException asapException) {
                    Log.writeLog(this, asapException.getLocalizedMessage());
                } catch (InterruptedException e) {
                    Log.writeLog(this, e.getLocalizedMessage());
                } catch(Throwable t) {
                    Log.writeLog(this, t.getLocalizedMessage());
                }
                finally {
                    HubConnectorImpl.this.dataChannelEstablishmentThread = null;
                }
            }

            // re-launch HubProtocolEngine
            HubConnectorImpl.this.restartHubProtocolEngine();
            // end thread
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////
    //                        hub management protocol engine (connector side)                         //
    ////////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * This thread reads PDU from hub and re-acts. First expected PDU is a re-action of its registration.
     */
    class HubConnectorProtocolEngine extends Thread {
        private final InputStream hubIS;
        private final OutputStream hubOS;
        private boolean again;
        private Thread thread;

        public HubConnectorProtocolEngine(InputStream hubIS, OutputStream hubOS) {
            this.hubIS = hubIS;
            this.hubOS = hubOS;
            this.again = true;
        }

        public void run() {
            Log.writeLog(this, "launch management protocol engine connector side");
            this.thread = Thread.currentThread();
            try {
                while(this.again) {
                    //Log.writeLog(this, "before read: " + localPeerID);
                    HubPDU hubPDU = HubPDU.readPDU(hubIS);
                    //Log.writeLog(this, "after read: " + localPeerID);

                    // get a silent request
                    if (hubPDU instanceof HubPDUSilentRQ) {
                        Log.writeLog(this, "got request: silent request");
                        HubPDUSilentRQ silentRQ = (HubPDUSilentRQ) hubPDU;
                        // calculate local time
                        HubConnectorImpl.this.silentUntil = System.currentTimeMillis() + silentRQ.waitDuration;
                        this.again = false; // end loop
                        // start DataChannelEstablishment
                        new Thread(new DataSessionEstablishmentThread(hubIS, hubOS, silentRQ.waitDuration)).start();
                    }

                    // got reply: new connection to peer established
                    else if (hubPDU instanceof HubPDUConnectPeerNewConnectionRPLY) {
                        Log.writeLog(this, "got reply connect peer");
                        this.handleConnectionRequest((HubPDUConnectPeerNewConnectionRPLY) hubPDU);
                    }

                    // got reply: new hub status
                    else if (hubPDU instanceof HubPDUHubStatusRPLY) {
                        Log.writeLog(this, "got reply hub status");
                        this.handleHubStatsRequest((HubPDUHubStatusRPLY) hubPDU);
                    }

                    // unknown PDU
                    else {
                        Log.writeLog(this, "cannot handle PDU, give up: "  + localPeerID + " | " + hubPDU);
                        break;
                    }
                }
            } catch (IOException | ASAPException e) {
                Log.writeLog(this, "connection lost to hub: " + localPeerID);
                e.printStackTrace();
            } catch (ClassCastException e) {
                Log.writeLog(this, "wrong pdu class - crazy: " + e.getLocalizedMessage());
            }

            Log.writeLog(this, "end connector hub protocol engine: " + localPeerID);
        }

        /////////// react on PDUs

        private void handleConnectionRequest(HubPDUConnectPeerNewConnectionRPLY hubPDU) {
            HubPDUConnectPeerNewConnectionRPLY hubPDUConnectPeerNewConnectionRPLY = (HubPDUConnectPeerNewConnectionRPLY) hubPDU;
            /*
            if(AbstractHubConnector.this.listener != null) {
                // create a connection
                Socket socket = new Socket(AbstractHubConnector.this.hostName, hubPDUNewConnectionRequest.port);

                // tell listener
                AbstractHubConnector.this.listener.notifyPeerConnected(
                        new PeerConnectionImpl(
                                hubPDUNewConnectionRequest.peerID,
                                socket.getInputStream(),
                                socket.getOutputStream()));

            }
             */
        }

        private void handleHubStatsRequest(HubPDUHubStatusRPLY hubPDU) {
            synchronized (HubConnectorImpl.this) {
                HubConnectorImpl.this.peerIDs = hubPDU.connectedPeers;
            }
        }

        public void kill() {
            this.again = false;
            if(this.thread != null) {
                this.thread.interrupt(); // nice try but would not help to get it out from read()
            }
        }
    }
}
