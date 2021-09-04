package net.sharksystem.hub.peerside;

import net.sharksystem.asap.ASAPEncounterManager;
import net.sharksystem.asap.ASAPException;
import net.sharksystem.asap.ASAPPeer;
import net.sharksystem.asap.EncounterConnectionType;
import net.sharksystem.hub.protocol.HubPDU;
import net.sharksystem.streams.StreamPair;
import net.sharksystem.utils.AlarmClock;
import net.sharksystem.utils.AlarmClockListener;
import net.sharksystem.utils.Log;

import java.io.IOException;
import java.util.*;

public class ASAPHubManagerImpl implements ASAPHubManager, Runnable, NewConnectionListener, AlarmClockListener {
    private static final int FORCE_NEW_ROUND_KEY = 1;
    private final ASAPEncounterManager asapEncounterManager;
    private final int waitIntervalInSeconds;
    private List<HubConnector> hubConnectors = new ArrayList<>();
    private int timeoutInMillis;
    private Thread managerThread = null;
    private int forceNewRoundWaitingPeriod = 2000; // some seconds - other connections can arrive..
    private AlarmClock alarmClock;

    public static ASAPHubManager startASAPHubManager(
            ASAPEncounterManager asapEncounterManager,  int waitIntervalInSeconds) {
        ASAPHubManagerImpl asapHubManager = new ASAPHubManagerImpl(asapEncounterManager, waitIntervalInSeconds);

        Log.writeLog(ASAPHubManagerImpl.class, "launch hub manager thread");
        new Thread(asapHubManager).start();
        return asapHubManager;
    }

    public static ASAPHubManager startASAPHubManager(ASAPEncounterManager asapEncounterManager) {
        return startASAPHubManager(asapEncounterManager, DEFAULT_WAIT_INTERVAL_IN_SECONDS);
    }

    public ASAPHubManagerImpl(ASAPEncounterManager asapEncounterManager) {
        this(asapEncounterManager, DEFAULT_WAIT_INTERVAL_IN_SECONDS);
    }

    public ASAPHubManagerImpl(ASAPEncounterManager asapEncounterManager,  int waitIntervalInSeconds) {
        this.asapEncounterManager = asapEncounterManager;
        this.waitIntervalInSeconds = waitIntervalInSeconds;
    }

    @Override
    public synchronized void addHub(HubConnector hubConnector) {
        Log.writeLog(this, this.toString(), "added hub connector: " + hubConnector);
        this.forceNewRound();
        this.hubConnectors.add(hubConnector);
        hubConnector.addListener(this);
    }

    /**
     * Here is the tricky thing: The manager thread waits for a given time before it ask for new status on hub.
     * Now, new hub connection can be added. What be could to have a new round earlier. But... new
     * connections could come in a larger number most probably.
     *
     * What we do: We set a timer. This time it rewound whenever this method is called again. So, the
     * last call within this timer period will determine the actual waiting period until we force another round.
     */
    private void forceNewRound() {
        if(this.alarmClock != null) {
            Log.writeLog(this, this.toString(), "kill old alarm");
            this.alarmClock.kill();
        }
        Log.writeLog(this, this.toString(), "set new alarm");
        this.alarmClock = new AlarmClock(forceNewRoundWaitingPeriod, FORCE_NEW_ROUND_KEY, this);
        this.alarmClock.start();
    }

    @Override
    public void alarmClockRinging(int key) {
        switch (key) {
            case FORCE_NEW_ROUND_KEY:
                this.alarmClock = null;
                Log.writeLog(this, this.toString(),"alarm clock ringing");
                if(this.managerThread != null) this.managerThread.interrupt();
                break;

            default: Log.writeLog(this, this.toString(),
                    "got an alarm with unknown key: (" + key + ") - ignored");
        }
    }

    public void kill() {
        Log.writeLog(this, this.toString(), "kill manager thread - if available");
        this.managerThreadStopped = true;
        if(this.managerThread != null)
            Log.writeLog(this, this.toString(), "call interrupt in manager thread");
            this.managerThread.interrupt();
    }

    @Override
    public synchronized void removeHub(HubConnector hubConnector) {
        Log.writeLog(this, this.toString(), "remove hub connector: " + hubConnector);
        this.hubConnectors.remove(hubConnector);
        hubConnector.removeListener(this);
    }

    public void setTimeOutInMillis(int millis) {
        this.timeoutInMillis = millis;
    }

    /////////////////////////////////////////////////////////////////////////////////////////////////////////////
    //                                                 bulk import                                             //
    /////////////////////////////////////////////////////////////////////////////////////////////////////////////

    public void connectASAPHubs(Collection<HubConnectorDescription> descriptions,
                                ASAPPeer asapPeer, boolean killNotDescribed) {
        // for each description
        for(HubConnectorDescription hcd : descriptions) {
            // already running?
            boolean isRunning = false; // assume not
            for(HubConnector runningHc : this.hubConnectors) {
                if(runningHc.isSame(hcd)) {
                    isRunning = true;
                    break;
                }
            }

            if(!isRunning) {
                // launch it
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        Log.writeLog(ASAPHubManagerImpl.this,ASAPHubManagerImpl.this.toString(),
                                "init new hub connector: " + hcd);
                        try {
                            HubConnector hubConnector = HubConnectorFactory.createHubConnector(hcd);
                            // register on hub
                            hubConnector.connectHub(asapPeer.getPeerID());
                            ASAPHubManagerImpl.this.addHub(hubConnector);
                            Log.writeLog(ASAPHubManagerImpl.this,ASAPHubManagerImpl.this.toString(),
                                    "hub connector initialized: " + hcd);

                        } catch (IOException | ASAPException e) {
                            Log.writeLog(ASAPHubManagerImpl.this,ASAPHubManagerImpl.this.toString(),
                                    "cannot create hub connector: " + e.getLocalizedMessage());
                        }
                    }
                }).start();
            } else {
                Log.writeLog(this, this.toString(), "hub connector already running: " + hcd);
            }
        } // end each description for-loop

        if(killNotDescribed) {
            ///////////////////// kill open connections which are not in the list
            Collection<HubConnector> toBeKilled = new ArrayList<>();

            for (HubConnector runningHc : this.hubConnectors) {
                boolean found = false;
                for (HubConnectorDescription hcd : descriptions) {
                    if (runningHc.isSame(hcd)) {
                        found = true;
                        break;
                    }
                }
                if (!found) toBeKilled.add(runningHc);
            }

            this.disconnectASAPHubs(toBeKilled);
        }
    }

    private void disconnectASAPHubs(Collection<HubConnector> toBeKilled) {
        for(HubConnector hcd : toBeKilled) {
                // try to disconnect
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                    try {
                        Log.writeLog(ASAPHubManagerImpl.this,ASAPHubManagerImpl.this.toString(),
                                "disconnect hub connector: " + hcd);
                        hcd.disconnectHub();
                        ASAPHubManagerImpl.this.removeHub(hcd);
                    } catch(IOException | ASAPException e) {
                        Log.writeLog(ASAPHubManagerImpl.this,ASAPHubManagerImpl.this.toString(),
                                e.getLocalizedMessage());
                    }
                }
            }).start();
        }
    }

    /**
     * Disconnect all running hubs
     */
    public void disconnectASAPHubs() {
        // clear hub connector list
        Collection<HubConnector> toBeKilled = this.hubConnectors;
        this.hubConnectors = new ArrayList<>();
        this.disconnectASAPHubs(toBeKilled);
    }

    private boolean managerThreadStopped = false;

    @Override
    public void run() {
        this.managerThread = Thread.currentThread();
        Log.writeLog(this, this.toString(), "hub manager thread running");

        while (!this.managerThreadStopped) {
            long startTime = System.currentTimeMillis();
            List<HubConnector> removeHub = new ArrayList<>();

            Log.writeLog(this, this.toString(), "start a new round");
            // start another round

            // start checker thread
            for(HubConnector hubConnector : this.hubConnectors) {
                new HubConnectorSyncThread(
                        this, this.asapEncounterManager, hubConnector, this.timeoutInMillis
                ).start();
            }

            try {
                long sleepingTime = this.waitIntervalInSeconds * 1000;
                Log.writeLog(this, this.toString(), "wait before next round (in ms): " + sleepingTime);
                Thread.sleep(sleepingTime);
            } catch (InterruptedException e) {
                if(!this.managerThreadStopped) {
                    Log.writeLog(this, this.toString(), "interrupted - make next round earlier");
                }
            }
            Log.writeLog(this, this.toString(), "hub manager thread ended.");
        }
    }

    @Override
    public void notifyPeerConnected (CharSequence targetPeerID, StreamPair streamPair){
        try {
            this.asapEncounterManager.handleEncounter(streamPair, EncounterConnectionType.ASAP_HUB);
        } catch (IOException e) {
            Log.writeLogErr(this, this.toString(), "cannot handle peer encounter: "
                    + e.getLocalizedMessage());
        }
    }

    public String toString() {
        return this.asapEncounterManager.toString();
    }

    class HubConnectorSyncThread extends Thread {
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
                this.hubConnector.prepareBlockUntilReceived(HubPDU.HUB_STATUS_REPLY);
                this.hubConnector.syncHubInformation();
                this.hubConnector.setTimeOutInMillis(this.timeoutInMillis);
                // now block - wait for reply
                this.hubConnector.blockUntilReceived(HubPDU.HUB_STATUS_REPLY);
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

        public String toString() {
            return this.asapEncounterManager.toString();
        }
    }
}