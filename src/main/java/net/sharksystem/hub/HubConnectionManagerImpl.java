package net.sharksystem.hub;

import net.sharksystem.SharkException;
import net.sharksystem.asap.ASAPEncounterManager;
import net.sharksystem.asap.ASAPPeer;
import net.sharksystem.hub.peerside.ASAPHubManagerImpl;
import net.sharksystem.hub.peerside.HubConnector;
import net.sharksystem.hub.peerside.HubConnectorDescription;

import java.io.IOException;
import java.util.List;

/**
 * That class merges encounter and asap hub management
 */
public class HubConnectionManagerImpl extends BasicHubConnectionManager
        implements HubConnectionManagerMessageHandler {
    private final ASAPPeer asapPeer;
    private ASAPHubManagerImpl hubManager;

    /**
     * decorator
     */
    protected void syncLists() {
        this.refreshHubList(); // refresh first
        super.syncLists();
    }

    public void connectHubs(List<HubConnectorDescription> hcdList) throws SharkException, IOException {
        if(hcdList == null || hcdList.isEmpty()) return;
        this.syncLists();
        for(HubConnectorDescription hcd : hcdList) {
            super.connectHub(hcd);
        }
        this.connectionChanged(true);
    }

    public void connectHub(HubConnectorDescription hcd) throws SharkException, IOException {
        this.syncLists();
        super.connectHub(hcd);
        this.connectionChanged(hcd, true);
    }

    public void disconnectHub(HubConnectorDescription hcd) throws SharkException, IOException {
        this.syncLists();
        super.disconnectHub(hcd);
        this.connectionChanged(hcd, false);
    }

    @Override
    public HubConnector getHubConnector(HubConnectorDescription hcd) throws SharkException {
        return this.hubManager.getHubConnector(hcd);
    }

    private Thread hubManangerThread = null;

    //////////////// hub new connection listener management
    public void addNewConnectedHubListener(NewHubConnectedListener connectedHubListener) {
        if(this.hubManager != null) {
            this.hubManager.addNewConnectedHubListener(connectedHubListener);
        }
    }

    public void removeNewConnectedHubListener(NewHubConnectedListener connectedHubListener) {
        if(this.hubManager != null) {
            this.hubManager.removeNewConnectedHubListener(connectedHubListener);
        }
    }

    public long getLastSyncTime() {
        if(this.hubManager == null) return -1;
        return this.hubManager.getLastSynchedWithConnectedHubs();
    }

    public void forceSync() {
        if(this.hubManager != null) {
            this.hubManager.forceSyncWithHubs();
        }
    }

    public HubConnectionManagerImpl(
            ASAPEncounterManager encounterManager, ASAPPeer asapPeer, int waitIntervalInSeconds) {
        this.asapPeer = asapPeer;
        this.hubManager = new ASAPHubManagerImpl(encounterManager, waitIntervalInSeconds);
        // run it
        this.hubManangerThread = new Thread(this.hubManager);
        this.hubManangerThread.start();
    }

    public HubConnectionManagerImpl(ASAPEncounterManager encounterManager, ASAPPeer asapPeer) {
        this(encounterManager, asapPeer, ASAPHubManagerImpl.DEFAULT_WAIT_INTERVAL_IN_SECONDS);
    }

    public void stopThreads() {
        this.hubManager.kill();
    }

    /* this first parameter hcd is useless ?? */
    @Override
    public void connectionChanged(HubConnectorDescription hcd, boolean connect) {
        // list are already in order - tell hub manager
        this.hubManager.connectASAPHubs(this.hcdList, this.asapPeer, true);
    }

    public void connectionChanged(boolean connect) {
        // list are already in order - tell hub manager
        this.hubManager.connectASAPHubs(this.hcdList, this.asapPeer, true);
    }

    @Override
    public void refreshHubList() {
        // ask hub manager for a fresh list
        this.hcdListHub = this.hubManager.getRunningConnectorDescriptions();
    }
}
