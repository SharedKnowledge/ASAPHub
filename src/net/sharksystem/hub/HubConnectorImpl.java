package net.sharksystem.hub;

import net.sharksystem.asap.ASAPException;
import net.sharksystem.utils.Log;

import java.io.*;
import java.util.ArrayList;
import java.util.Collection;

public class HubConnectorImpl implements HubConnector {
    private final InputStream hubIS;
    private final OutputStream hubOS;

    private NewConnectionListener listener;
    private HubConnectorProtocolEngine hubConnectorProtocolEngine;
    private Collection<CharSequence> peerIDs = new ArrayList<>();
    private CharSequence localPeerID;

    private long silentUntil = 0;

    public HubConnectorImpl(InputStream hubIS, OutputStream hubOS) {
        this.hubIS = hubIS;
        this.hubOS = hubOS;
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////
    //                                 guard methods - ensure right status                            //
    ////////////////////////////////////////////////////////////////////////////////////////////////////

    private void checkConnected() throws IOException {
        if(this.hubConnectorProtocolEngine == null) throw new IOException("no hub connection yet");
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////
    //                                   mapping API - protocol engine                                //
    ////////////////////////////////////////////////////////////////////////////////////////////////////

    public void connectHub(CharSequence localPeerID) throws IOException {
        this.localPeerID = localPeerID;
        // create hello pdu
        HubPDURegister hubPDURegister = new HubPDURegister(localPeerID);

        // introduce yourself to hub
        hubPDURegister.sendPDU(this.hubOS);

        // start management protocol
        this.hubConnectorProtocolEngine = new HubConnectorProtocolEngine(this.hubIS, this.hubOS);
        this.hubConnectorProtocolEngine.start();
    }

    @Override
    public void syncHubInformation() throws IOException {
        this.checkConnected();
        new HubPDUHubStatusRQ().sendPDU(this.hubOS);
    }

    @Override
    public void connectPeer(CharSequence peerID) throws IOException {
        this.checkConnected();

        // create hello pdu
        HubPDUConnectPeerRQ hubPDUConnectPeerRQ = new HubPDUConnectPeerRQ(peerID);

        // ask for connection
        hubPDUConnectPeerRQ.sendPDU(this.hubOS);
    }

    @Override
    public void disconnectHub() throws IOException {
        this.localPeerID = null;
        this.hubConnectorProtocolEngine.kill();
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

    ////////////////////////////////////////////////////////////////////////////////////////////////////
    //                                data connection establishment                                   //
    ////////////////////////////////////////////////////////////////////////////////////////////////////

    private void startHubProtocolEngine() {
        Log.writeLog(this, "start hub protocol engine");
        (new HubConnectorProtocolEngine(this.hubIS, this.hubOS)).start();
    }

    private class RelaunchHubProtocolEngineThread extends Thread {
        private final long duration;
        private final Thread thread2interrupt;

        RelaunchHubProtocolEngineThread(long duration, Thread thread2interrupt) {
            this.duration = duration;
            this.thread2interrupt = thread2interrupt;
        }
        public void run() {
            Log.writeLog(this, "wait");
            try {
                Thread.sleep(duration);
                Log.writeLog(this, "interrupt reader thread");
                this.thread2interrupt.interrupt();
                HubConnectorImpl.this.startHubProtocolEngine();
            } catch (InterruptedException e) {
                Log.writeLog(this, "interrupted");
            }
        }
    }

    private class DataChannelEstablishmentThread implements Runnable {
        private final InputStream hubIS;
        private final OutputStream hubOS;
        private final long remainSilentInMillis;

        public DataChannelEstablishmentThread(InputStream hubIS, OutputStream hubOS, long remainSilentInMillis) {
            this.hubIS = hubIS;
            this.hubOS = hubOS;
            this.remainSilentInMillis = remainSilentInMillis;
        }

        public void run() {
            if (HubConnectorImpl.this.silentUntil >= System.currentTimeMillis()) {
                // still valid - tell hub we are ready here
                Log.writeLog(this, "send silent reply - remain silent in ms " + this.remainSilentInMillis);

                HubPDUSilentRPLY silentRPLY = new HubPDUSilentRPLY(this.remainSilentInMillis);
                try {
                    silentRPLY.sendPDU(this.hubOS);
                    // no wait to launch - TODO: introduce an alarm clock to kick us back to hub protocol

                    HubPDU hubPDU = HubPDU.readPDU(this.hubIS);

                    // got a channel clear pdu
                    if (hubPDU instanceof HubPDUChannelClear) {
                        HubPDUChannelClear channelClear = (HubPDUChannelClear) hubPDU;
                        Log.writeLog(this, "channel is clear - maxIdleInMillis == "
                                + channelClear.maxIdleInMillis);

                        // link
                        /* separate app side from open streams. An app might close a stream and we
                         do not want this. We want them open after this data session
                         */

                        /* application gets an input stream (appSideOS) which gets its data from
                        thisSideOS which is filled by reading from hubIS (the established input stream to hub)
                         */
                        PipedOutputStream thisSideOS = new PipedOutputStream();
                        PipedInputStream appSideIS = new PipedInputStream(thisSideOS);
                        StreamLink streamLink2App = new StreamLink(this.hubIS, thisSideOS,
                                channelClear.maxIdleInMillis, false);

                        // other way around
                        PipedInputStream thisSideIS = new PipedInputStream();
                        PipedOutputStream appSideOS = new PipedOutputStream(thisSideIS);
                        StreamLink streamLinkFromApp = new StreamLink(thisSideIS, this.hubOS,
                                channelClear.maxIdleInMillis, false);

                        // who is partner?
                        CharSequence otherPeerID =
                            channelClear.sourcePeerID.toString().equalsIgnoreCase(localPeerID.toString()) ?
                                    channelClear.targetPeerID : channelClear.sourcePeerID;

                        // create structure to tell app
                        DataSessionConnection newConnection =
                                new DataSessionConnection(appSideIS, appSideOS, otherPeerID);

                        // launch links
                        streamLink2App.start();
                        streamLinkFromApp.start();

                        // tell listener
                        listener.notifyPeerConnected(newConnection);

                        // wait processes to die
                        streamLink2App.join();
                        streamLinkFromApp.join();
                        // that's it. Another pending and valid data stream request?
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
            }

            // re-launch HubProtocolEngine
            HubConnectorImpl.this.startHubProtocolEngine();
            // end thread
        }
    }

    class DataSessionConnection implements HubSessionConnection {
        private final InputStream is;
        private final OutputStream os;
        private final CharSequence peerSourceID;

        DataSessionConnection(InputStream is, OutputStream os, CharSequence peerID) {
            this.is = is;
            this.os = os;
            this.peerSourceID = peerID;
        }

        @Override
        public InputStream getInputStream() {
            return this.is;
        }

        @Override
        public OutputStream getOutputStream() {
            return this.os;
        }

        @Override
        public CharSequence getPeerID() {
            return this.peerSourceID;
        }

        @Override
        public void close() throws IOException {
            this.is.close();
            this.os.close();
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
                        new Thread(new DataChannelEstablishmentThread(hubIS, hubOS, silentRQ.waitDuration)).start();
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
