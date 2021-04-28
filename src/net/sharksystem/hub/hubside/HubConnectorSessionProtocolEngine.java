package net.sharksystem.hub.hubside;

import net.sharksystem.asap.ASAPException;
import net.sharksystem.hub.protocol.*;
import net.sharksystem.utils.Log;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

class HubConnectorSessionProtocolEngine extends Thread {
    private final SharedStreamPairConnectorHubSideImpl hubConnectorSession;
    boolean again = true;
    private boolean silenceRQCalled = false;

    public HubConnectorSessionProtocolEngine(SharedStreamPairConnectorHubSideImpl hubConnectorSession) {
        this.hubConnectorSession = hubConnectorSession;
    }

    void silenceRQ(long duration) throws IOException {
        if (this.silenceRQCalled) {
            Log.writeLog(this, "silenceRQ already called - do not do anything: " + hubConnectorSession.getPeerID());
            return; // already called
        }

        this.silenceRQCalled = true;

        /* we are going to send a message to the connector. It is sent very fast.
        It is highly unlikely that a pdu is processed and both threads interfere. To even avoid
        this situation we synchronize with read
         */
        synchronized (this) {
            Log.writeLog(this, "send silent request to connector: " + hubConnectorSession.getPeerID());
            new HubPDUSilentRQ(duration).sendPDU(hubConnectorSession.getOutputStream());
        }
    }

    @Override
    public void run() {
//        hubConnectorSession.hubProtocolThread = this; // remember read thread to interrupt

        try {
            Log.writeLog(this, "launch hub session with: " + hubConnectorSession.getPeerID());
            while (this.again) {
                // read - will most probably block
                //Log.writeLog(this, "before read from " + HubSession.this.peerID);
                HubPDU hubPDU = HubPDU.readPDU(hubConnectorSession.getInputStream());
                synchronized (this) {
                    int syncWithSilentRQ = 42; // we need a line of code to stop sync the process
                }
                //Log.writeLog(this, "received from " + HubSession.this.peerID);

                ///// handle PDUs
                if (hubPDU instanceof HubPDUHubStatusRQ) {
                    Log.writeLog(this, "got hub status RQ from " + hubConnectorSession.getPeerID());
//                    hubConnectorSession.handleHubStatusRQ((HubPDUHubStatusRQ) hubPDU);
                    this.handleHubStatusRQ((HubPDUHubStatusRQ) hubPDU);
                }

                /* Do not throw this away - must be re-integrated
                else if (hubPDU instanceof HubPDUConnectPeerNewTCPSocketRQ) {
                    Log.writeLog(this, this.peerID + ": connect peer RQ new tcp socket");
                    this.handleConnectPeerRQNewTCPSocket((HubPDUConnectPeerNewTCPSocketRQ) hubPDU);
                }
                */
                ///////// hub connect rq - use open hub streams
                else if (hubPDU instanceof HubPDUConnectPeerRQ) {
                    Log.writeLog(this, "got hub connect RQ from " + hubConnectorSession.getPeerID());
                    HubPDUConnectPeerRQ connectRQ = (HubPDUConnectPeerRQ) hubPDU;

                    // ask hub to establish a silent connection to this peer - asynchronous call
//                    hubConnectorSession.hubInternal.connectionRequest(connectRQ.peerID, hubConnectorSession);
//                    this.hubConnectorSession.getHub().connect(connectRQ.peerID, this.hubConnectorSession);
                    this.hubConnectorSession.connectionRequest(connectRQ.peerID);

                }

                ///////// hub silent reply
                else if (hubPDU instanceof HubPDUSilentRPLY) {
                    Log.writeLog(this, "got silent reply from " + hubConnectorSession.getPeerID());
                    // connection is silent now
                    HubPDUSilentRPLY silentRPLY = (HubPDUSilentRPLY) hubPDU;
                    this.again = false; // end this thread
                    this.hubConnectorSession.enterSilence(silentRPLY.waitDuration);
                } else {
                    Log.writeLog(this, "got unknown PDU type from " + hubConnectorSession.getPeerID());
                }
            }
        } catch (IOException | ASAPException e) {
            Log.writeLog(this, "connection lost to: " + hubConnectorSession.getPeerID());
            Log.writeLog(this, "remove connection to: " + hubConnectorSession.getPeerID());
            this.hubConnectorSession.sessionEnded();
        } catch (ClassCastException e) {
            Log.writeLog(this, "wrong pdu class - crazy: " + e.getLocalizedMessage());
        } finally {
            Log.writeLog(this, "end hub session with: " + hubConnectorSession.getPeerID());
        }
    }


    private void handleHubStatusRQ(HubPDUHubStatusRQ hubPDU) throws IOException {
        this.sendHubStatusRPLY();
    }

    private void sendHubStatusRPLY() throws IOException {
        Set<CharSequence> allPeers = this.hubConnectorSession.getHub().getRegisteredPeers();
        // sort out calling peer
        //Log.writeLog(this, "assemble registered peer list for " + this.peerID);
        Set<CharSequence> peersWithoutCaller = new HashSet();
        for (CharSequence peerName : allPeers) {
//            Log.writeLog(this, peerName + " checked for peer list for " + this.peerID);
            if (!peerName.toString().equalsIgnoreCase(this.hubConnectorSession.getPeerID().toString())) {
                peersWithoutCaller.add(peerName);
            }
        }
        HubPDU hubInfoPDU = new HubPDUHubStatusRPLY(peersWithoutCaller);
        Log.writeLog(this, "send hub status to " + this.hubConnectorSession.getPeerID());
        hubInfoPDU.sendPDU(this.hubConnectorSession.getOutputStream());
    }
}
