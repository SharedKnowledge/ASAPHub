package net.sharksystem.hub.peerside;

import net.sharksystem.SharkException;
import net.sharksystem.asap.*;
import net.sharksystem.hub.*;
import net.sharksystem.utils.AlarmClock;
import net.sharksystem.utils.AlarmClockListener;
import net.sharksystem.utils.Log;
import net.sharksystem.utils.streams.StreamPair;

import java.io.IOException;
import java.util.*;

public class ASAPHubManagerImpl implements ASAPHubManager, NewHubConnectionListenerManagement,
        Runnable, NewConnectionListener, AlarmClockListener,
        HubConnectorStatusListener {
    private static final int FORCE_NEW_ROUND_KEY = 1;
    private final ASAPEncounterManager asapEncounterManager;
    private final int waitIntervalInSeconds;
    private List<HubConnector> hubConnectors = new ArrayList<>();
    private int timeoutInMillis;
    private Thread managerThread = null;
    private int forceNewRoundWaitingPeriod = 2000; // some seconds - other connections can arrive..
    private AlarmClock alarmClock;

    public static ASAPHubManager createASAPHubManager(
            ASAPEncounterManager asapEncounterManager,  int waitIntervalInSeconds) {

        return new ASAPHubManagerImpl(asapEncounterManager, waitIntervalInSeconds);
    }

    public static ASAPHubManager createASAPHubManager(ASAPEncounterManager asapEncounterManager) {

        return new ASAPHubManagerImpl(asapEncounterManager, DEFAULT_WAIT_INTERVAL_IN_SECONDS);
    }

    public ASAPHubManagerImpl(ASAPEncounterManager asapEncounterManager) {
        this(asapEncounterManager, DEFAULT_WAIT_INTERVAL_IN_SECONDS);
    }

    public ASAPHubManagerImpl(ASAPEncounterManager asapEncounterManager,  int waitIntervalInSeconds) {
        this.asapEncounterManager = asapEncounterManager;
        this.waitIntervalInSeconds = waitIntervalInSeconds;
    }

    @Override
    public void addHub(HubConnector hubConnector) {
        Log.writeLog(this, this.toString(), "added hub connector: " + hubConnector);
        synchronized(this.hubConnectors) {
            this.hubConnectors.add(hubConnector);
            hubConnector.addListener(this);

            if(this.managerThread == null) {
                // re-launch
                this.managerThreadStopped = false;
                new Thread(this).start();
            } else {
                // we have a running manager thread
                this.forceNewRound();
            }
        }
    }

    @Override
    public void removeHub(HubConnector hubConnector) {
        Log.writeLog(this, this.toString(), "remove hub connector: " + hubConnector);
        synchronized(this.hubConnectors) {
            this.hubConnectors.remove(hubConnector);
            hubConnector.removeListener(this);
            if (this.hubConnectors.size() == 0) {
                Log.writeLog(this, this.toString(), "no more connector - shut down hub manager thread");
                this.kill();
            }
        }
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
            //Log.writeLog(this, this.toString(), "kill old alarm");
            this.alarmClock.kill();
        }
        //Log.writeLog(this, this.toString(), "set new alarm");
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

    public void setTimeOutInMillis(int millis) {
        this.timeoutInMillis = millis;
    }

    /////////////////////////////////////////////////////////////////////////////////////////////////////////////
    //                                                 bulk import                                             //
    /////////////////////////////////////////////////////////////////////////////////////////////////////////////

    private List<HubConnectorDescription> runningConnectorDescriptions = new ArrayList<>();

    public synchronized List<HubConnectorDescription> getRunningConnectorDescriptions() {
        return this.runningConnectorDescriptions;
    }

    public HubConnector getHubConnector(HubConnectorDescription hcd) throws SharkException {
        for(HubConnector runningHc : this.hubConnectors) {
            if(runningHc.isSame(hcd)) return runningHc;
        }

        throw new SharkException("no connection to described hub available");
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////
    //                                 new hub connector listener management                              //
    ////////////////////////////////////////////////////////////////////////////////////////////////////////
    private Set<NewHubConnectedListener> connectedHubListenerList = new HashSet<>();
    public void addNewConnectedHubListener(NewHubConnectedListener connectedHubListener) {
        this.connectedHubListenerList.add(connectedHubListener);
    }

    public void removeNewConnectedHubListener(NewHubConnectedListener connectedHubListener) {
        this.connectedHubListenerList.remove(connectedHubListener);
    }

    private void notifyNewHubConnectionListener(HubConnectorDescription hcd) {
        for(NewHubConnectedListener connectedHubListener : this.connectedHubListenerList) {
            connectedHubListener.newHubConnected(hcd);
        }
    }

    private void hubConnectorStarted(HubConnectorDescription hcd) {
        this.runningConnectorDescriptions.add(hcd);
        this.notifyNewHubConnectionListener(hcd);
    }

    public void connectASAPHubs(Collection<HubConnectorDescription> descriptions,
                                ASAPPeer asapPeer, boolean killConnectionIfNotInList) {
        // for each description
        for(HubConnectorDescription hcd : descriptions) {
            // already running?
            boolean isRunning = false; // assume not
            for(HubConnector runningHc : this.hubConnectors) {
                if(runningHc.isSame(hcd)) {
                    isRunning = true;
                    this.runningConnectorDescriptions.add(hcd);
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
                            // remember - it is running now
//                            ASAPHubManagerImpl.this.runningConnectorDescriptions.add(hcd);
                            ASAPHubManagerImpl.this.hubConnectorStarted(hcd);
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

        if(killConnectionIfNotInList) {
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
                if (!found) {
                    toBeKilled.add(runningHc);
                    this.runningConnectorDescriptions.remove(runningHc);
                }
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

    public void forceSyncWithHubs() {
        if(this.managerThread != null) {
            this.managerThread.interrupt();
        }
    }

    private boolean managerThreadStopped = false;

    private long lastSyncStatusWithConnectedHubs = -1;

    public long getLastSynchedWithConnectedHubs() {
        return this.lastSyncStatusWithConnectedHubs;
    }

    @Override
    public void run() {
        this.managerThread = Thread.currentThread();
        Log.writeLog(this, this.toString(), "hub manager thread started");

        while (!this.managerThreadStopped) {
            Log.writeLog(this, this.toString(),"start a new sync round");

            this.lastSyncStatusWithConnectedHubs = System.currentTimeMillis();
            for(HubConnector hubConnector : this.hubConnectors) {
                Log.writeLog(this, this.toString(), "check hub connection");
                hubConnector.addStatusListener(this);
                try {
                    // trigger syncing
                    hubConnector.syncHubInformation();
                } catch (IOException e) {
                    // io on this hub - removeHub it later and go ahead
                    Log.writeLog(this, this.toString(), "problems with hub - remove it: " + e);
                    e.printStackTrace();
                    removeHub(hubConnector);
                }
            }

            try {
                long sleepingTime = this.waitIntervalInSeconds * 1000;
                Log.writeLog(this, this.toString(), "wait before next round (in ms): " + sleepingTime);
                Thread.sleep(sleepingTime);
            } catch (InterruptedException e) {
                if(!this.managerThreadStopped) {
                    Log.writeLog(this, this.toString(), "interrupted - make next sync round now");
                }
            }
        }
        Log.writeLog(this, this.toString(), "hub manager thread ended.");
    }


    @Override
    public void notifyConnectedAndOpen() {
        // ignore
        Log.writeLog(this, this.toString(), "notifyConnectedAndOpen");
    }

    @Override
    public void notifySynced(Connector connector, boolean changed) {
        Log.writeLog(this, this.toString(), "synced (changed: " + changed + ")");

        if(/*changed && */ connector instanceof HubConnector) { // maybe new message in ASAP peer... connect each round
            HubConnector hubConnector = (HubConnector) connector;
            try {
                Collection<CharSequence> peerIDs = hubConnector.getPeerIDs();
                Log.writeLog(this, this.toString(), "got peerIDs: " + peerIDs);
                if (peerIDs != null && !peerIDs.isEmpty()) for (CharSequence peerID : peerIDs) {
                    if (this.asapEncounterManager.shouldCreateConnectionToPeer(
                            peerID, ASAPEncounterConnectionType.ASAP_HUB)) {
                        hubConnector.connectPeer(peerID);
                    }
                }
            } catch (IOException e) {
                // io on this hub - removeHub it later and go ahead
                Log.writeLog(this, this.toString(), "problems with hub - remove it: " + e);
                e.printStackTrace();
                this.removeHub(hubConnector);
            }
        }
    }

    @Override
    public void notifyPeerConnected(CharSequence targetPeerID, StreamPair streamPair) {
        try {
            // can be a race condition - this side did not initiate connection establishment
//            this.asapEncounterManager.handleEncounter(streamPair, ASAPEncounterConnectionType.ASAP_HUB, false);
            this.asapEncounterManager.handleEncounter(streamPair, ASAPEncounterConnectionType.ASAP_HUB);
        } catch (IOException e) {
            Log.writeLogErr(this, this.toString(), "cannot handle peer encounter: "
                    + e.getLocalizedMessage());
        }
    }

    public String toString() {
        return this.asapEncounterManager.toString();
    }
}