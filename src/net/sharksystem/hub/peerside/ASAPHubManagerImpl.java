package net.sharksystem.hub.peerside;

import net.sharksystem.asap.ASAPEncounterManager;
import net.sharksystem.asap.EncounterConnectionType;
import net.sharksystem.hub.protocol.HubPDU;
import net.sharksystem.hub.protocol.HubPDUHubStatusRPLY;
import net.sharksystem.streams.StreamPair;
import net.sharksystem.utils.Log;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class ASAPHubManagerImpl implements ASAPHubManager, Runnable, NewConnectionListener {
    private final ASAPEncounterManager asapEncounterManager;
    private final int waitIntervalInSeconds;
    private List<HubConnector> hubs = new ArrayList<>();
    private int timeoutInMillis;

    public ASAPHubManagerImpl(ASAPEncounterManager asapEncounterManager) {
        this(asapEncounterManager, DEFAULT_WAIT_INTERVAL_IN_SECONDS);
    }

    public ASAPHubManagerImpl(ASAPEncounterManager asapEncounterManager,  int waitIntervalInSeconds) {
        this.asapEncounterManager = asapEncounterManager;
        this.waitIntervalInSeconds = waitIntervalInSeconds;
    }

    @Override
    public void addHub(HubConnector hub) {
        this.hubs.add(hub);
        hub.addListener(this);
    }

    @Override
    public void removeHub(HubConnector hub) {
        this.hubs.remove(hub);
        hub.removeListener(this);
    }

    public void setTimeOutInMillis(int millis) {
        this.timeoutInMillis = millis;
    }

    @Override
    public void run() {
        Log.writeLog(this, "Hub manager thread running");
        for(;;) {
            long startTime = System.currentTimeMillis();
            List<HubConnector> removeHub = new ArrayList<>();

            Log.writeLog(this, "start a new round");
            // start another round

            // first - sync status
            for(HubConnector hubConnector : this.hubs) {
                try {
                    hubConnector.prepareBlockUntilReceived(HubPDU.HUB_STATUS_REPLY);
                    hubConnector.syncHubInformation();
                } catch (IOException e) {
                    // io on this hub - removeHub it later and go ahead
                    Log.writeLog(this, "problems with hub - remove it: " + e);
                    e.printStackTrace();
                    removeHub.add(hubConnector);
                }
            }

            for(HubConnector r : removeHub) {
                this.removeHub(r);
            }

            removeHub = new ArrayList<>();

            // wait a moment for reply
            for(HubConnector hubConnector : this.hubs) {
                Collection<CharSequence> peerIDs = null;
                try {
                    hubConnector.setTimeOutInMillis(this.timeoutInMillis);
                    hubConnector.blockUntilReceived(HubPDU.HUB_STATUS_REPLY);
                    peerIDs = hubConnector.getPeerIDs();
                } catch (IOException e) {
                    // io on this hub - removeHub it later and go ahead
                    Log.writeLog(this, "problems with hub - remove it: " + e);
                    e.printStackTrace();
                    removeHub.add(hubConnector);
                }

                Log.writeLog(this, "got peerIDs: " + peerIDs);
                if(peerIDs != null && !peerIDs.isEmpty()) {
                    for(CharSequence peerID : peerIDs) {
                        if(this.asapEncounterManager.shouldCreateConnectionToPeer(
                                peerID, EncounterConnectionType.ASAP_HUB)) {
                            try {
                                hubConnector.connectPeer(peerID);
                            } catch (IOException e) {
                                Log.writeLog(this, "exception when asking for connection: "
                                        + e.getLocalizedMessage());
                                removeHub.add(hubConnector);
                            }
                        }
                    }
                }
            }
            for(HubConnector r : removeHub) {
                this.removeHub(r);
            }

            long duration = System.currentTimeMillis() - startTime;
            Log.writeLog(this, "made a round through all hubs. Took (in ms): " + duration);

            try {
                long sleepingTime = this.waitIntervalInSeconds*1000 - duration;
                Log.writeLog(this, "wait before next round (in ms): " + sleepingTime);
                Thread.sleep(sleepingTime);
            } catch (InterruptedException e) {
                // ignore
            }
        }
    }

    @Override
    public void notifyPeerConnected(CharSequence targetPeerID, StreamPair streamPair) {
        try {
            this.asapEncounterManager.handleEncounter(streamPair, EncounterConnectionType.ASAP_HUB);
        } catch (IOException e) {
            Log.writeLogErr(this, "cannot handle peer encounter: " + e.getLocalizedMessage());
        }
    }
}
