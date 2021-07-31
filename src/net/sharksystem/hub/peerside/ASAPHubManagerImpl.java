package net.sharksystem.hub.peerside;

import net.sharksystem.asap.ASAPEncounterManager;
import net.sharksystem.asap.EncounterConnectionType;
import net.sharksystem.streams.StreamPair;
import net.sharksystem.utils.Log;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class ASAPHubManagerImpl implements ASAPHubManager, Runnable, NewConnectionListener {
    private final ASAPEncounterManager asapEncounterManager;
    private final long waitIntervalInMillis;
    private List<HubConnector> hubs;

    public ASAPHubManagerImpl(ASAPEncounterManager asapEncounterManager) {
        this(asapEncounterManager, DEFAULT_WAIT_INTERVAL_IN_SECONDS);
    }

    public ASAPHubManagerImpl(ASAPEncounterManager asapEncounterManager,  int waitIntervalInSeconds) {
        this.asapEncounterManager = asapEncounterManager;
        this.waitIntervalInMillis = waitIntervalInSeconds * 1000;
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

    @Override
    public void run() {
        for(;;) {
            long startTime = System.currentTimeMillis();
            List<HubConnector> removeHub = new ArrayList<>();

            // start another round
            for(HubConnector hubConnector : this.hubs) {
                Collection<CharSequence> peerIDs = null;
                try {
                    peerIDs = hubConnector.getPeerIDs();
                } catch (IOException e) {
                    // io on this hub - removeHub it later and go ahead
                    Log.writeLog(this, "problems with hub - remove it");
                    removeHub.add(hubConnector);
                }

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
                Thread.sleep(this.waitIntervalInMillis - duration);
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
