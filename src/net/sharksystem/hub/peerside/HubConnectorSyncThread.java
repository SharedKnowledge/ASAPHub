package net.sharksystem.hub.peerside;

import net.sharksystem.asap.ASAPEncounterManager;
import net.sharksystem.asap.EncounterConnectionType;
import net.sharksystem.hub.Connector;
import net.sharksystem.hub.protocol.HubPDU;
import net.sharksystem.utils.Log;

import java.io.IOException;
import java.util.Collection;

class HubConnectorSyncThread extends Thread implements HubConnectorStatusListener {
    private final ASAPHubManager hubManager;
    private final HubConnector hubConnector;
    private final int timeoutInMillis;
    private final ASAPEncounterManager asapEncounterManager;

    HubConnectorSyncThread(ASAPHubManager hubManager, ASAPEncounterManager asapEncounterManager,
                           HubConnector hubConnector, int timeoutInMillis) {
        this.hubManager = hubManager;
        this.asapEncounterManager = asapEncounterManager;
        this.hubConnector = hubConnector;
        this.timeoutInMillis = timeoutInMillis;
    }

    public void run() {
        try {
            // prepare a block - thread will be blocked until new status from hub came in
            Log.writeLog(this, this.toString(), "check hub connection");
            this.hubConnector.addStatusListener(this);
            //this.hubConnector.prepareBlockUntilReceived(HubPDU.HUB_STATUS_REPLY);
            this.hubConnector.syncHubInformation();
            //this.hubConnector.setTimeOutInMillis(this.timeoutInMillis);
            // now block - wait for reply
            //this.hubConnector.blockUntilReceived(HubPDU.HUB_STATUS_REPLY);

            /*
            Collection<CharSequence> peerIDs = hubConnector.getPeerIDs();

            Log.writeLog(this, this.toString(), "got peerIDs: " + peerIDs);
            if (peerIDs != null && !peerIDs.isEmpty()) for (CharSequence peerID : peerIDs) {
                if (this.asapEncounterManager.shouldCreateConnectionToPeer(
                        peerID, EncounterConnectionType.ASAP_HUB)) {
                    this.hubConnector.connectPeer(peerID);
                }
            }
             */
        } catch (IOException e) {
            // io on this hub - removeHub it later and go ahead
            Log.writeLog(this, this.toString(), "problems with hub - remove it: " + e);
            e.printStackTrace();
            this.hubManager.removeHub(this.hubConnector);
        }
    }

    @Override
    public void notifyConnectedAndOpen() {
        // ignore
    }

    @Override
    public void notifySynced(Connector connector, boolean changed) {
        Log.writeLog(this, this.toString(), "synced (changed: " + changed + ")");
        if(changed && connector instanceof HubConnector) {
            HubConnector hubConnector = (HubConnector) connector;
            try {
                Collection<CharSequence> peerIDs = hubConnector.getPeerIDs();
                Log.writeLog(this, this.toString(), "got peerIDs: " + peerIDs);
                if (peerIDs != null && !peerIDs.isEmpty()) for (CharSequence peerID : peerIDs) {
                    if (this.asapEncounterManager.shouldCreateConnectionToPeer(
                            peerID, EncounterConnectionType.ASAP_HUB)) {
                        this.hubConnector.connectPeer(peerID);
                    }
                }
            } catch (IOException e) {
                // io on this hub - removeHub it later and go ahead
                Log.writeLog(this, this.toString(), "problems with hub - remove it: " + e);
                e.printStackTrace();
                this.hubManager.removeHub(this.hubConnector);
            }
        }
    }

    public String toString() {
        return this.asapEncounterManager.toString();
    }
}
