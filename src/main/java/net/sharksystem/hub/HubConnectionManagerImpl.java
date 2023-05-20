package net.sharksystem.hub;

import net.sharksystem.SharkException;
import net.sharksystem.asap.ASAPEncounterManager;
import net.sharksystem.asap.ASAPPeer;
import net.sharksystem.hub.peerside.ASAPHubManagerImpl;
import net.sharksystem.hub.peerside.HubConnectorDescription;

import java.io.IOException;

public class HubConnectionManagerImpl extends BasicHubConnectionManager
        implements HubConnectionManager, HubConnectionManagerMessageHandler {
    private final ASAPPeer asapPeer;
    private ASAPHubManagerImpl hubManager;

    /**
     * decorator
     */
    protected void syncLists() {
        this.refreshHubList(); // refresh first
        super.syncLists();
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

    public HubConnectionManagerImpl(ASAPEncounterManager encounterManager, ASAPPeer asapPeer) {
        this.asapPeer = asapPeer;
        this.hubManager = new ASAPHubManagerImpl(encounterManager);
    }

    @Override
    public void connectionChanged(HubConnectorDescription hcd, boolean connect) {
        // list are already in order - tell hub manager
        this.hubManager.connectASAPHubs(this.hcdList, this.asapPeer, true);
    }

    @Override
    public void refreshHubList() {
        // ask hub manager for a fresh list
        this.hcdListHub = this.hubManager.getRunningConnectorDescriptions();
    }
}
