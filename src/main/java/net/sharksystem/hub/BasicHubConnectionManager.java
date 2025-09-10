package net.sharksystem.hub;

import net.sharksystem.SharkException;
import net.sharksystem.hub.peerside.HubConnectorDescription;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * A streamlined facade meant to be used on service side of an ASAP application that makes use of hubs.
 * This class assumes the proposed architecture: A connection handler (in most cases subclassed from ASAPPeer)
 * is wrapped into an encounter manager.
 * <p>
 * Instances of that hub connection manager class are initiated with that encounter manager and an ASAPPeer instance.
 * <p>
 * It provides just a few methods to connect and disconnect hubs. Some optional behaviour is hidden.
 */
public abstract class BasicHubConnectionManager implements HubConnectionManager {
    protected long lastConnectionAttempt = System.currentTimeMillis();

    // list outside hub - it can run out of sync with internal list in hub due to thread, connection losses etc.
    protected List<HubConnectorDescription> hcdList = new ArrayList<>();
    protected List<HubConnectorDescription> hcdListHub = new ArrayList<>();
    protected List<HubConnectionManager.FailedConnectionAttempt> failedConnectionAttempts = new ArrayList<>();

    private long lastSync = System.currentTimeMillis();

    /**
     * Hub manager can be asked to connect to or disconnect from hubs. We send a list of hubs which are to be
     * connected. Connection establishment can take a while, though. It is a wish list on application side for a while.
     * Connection establishment can fail - not any wish can be fulfilled.
     * <p>
     * We assume that this methode is called due to user interaction. Meaning: Intervals between two calls are long
     * enough to establish a connection or fail in the attempt. Implication: We assume: hub internal list is always
     * accurate. We can keep track of failed attempts. And we do.
     */
    protected void syncLists() {
        // wait at least a second for a new sync
        long now = System.currentTimeMillis();
        if (now - this.lastSync <= MINIMAL_TIME_BEFORE_NEXT_HUB_SYNC_IN_MILLIS) return;
        this.lastSync = now;

        // ask for current list from hub
        List<HubConnectorDescription> toBeRemoved = new ArrayList<>();
        for (HubConnectorDescription wishedConnection : this.hcdList) {
            boolean found = false;
            for (HubConnectorDescription runningConnection : this.hcdListHub) {
                if (wishedConnection.isSame(runningConnection)) {
                    found = true;
                    break; // found - go ahead.
                }
            }
            // remove failed attempt from connected hubs list
            if (!found) toBeRemoved.add(wishedConnection);
        }

        // remove and remember unfulfilled wishes
        for (HubConnectorDescription failedConnection : toBeRemoved) {
            // 1st remove from wish list
            this.hcdList.remove(failedConnection);

            HubConnectionManager.FailedConnectionAttempt recordedOldAttempt = null;
            // previous failed connection attempt recorded?
            for (HubConnectionManager.FailedConnectionAttempt recordedFailedConnection : this.failedConnectionAttempts) {
                if (recordedFailedConnection.getHubConnectorDescription().isSame(failedConnection)) {
                    recordedOldAttempt = recordedFailedConnection;
                    break;
                }
            }
            if (recordedOldAttempt != null) this.failedConnectionAttempts.remove(recordedOldAttempt);
            this.failedConnectionAttempts.add(new HubConnectionManager.FailedConnectionAttempt() {
                @Override
                public HubConnectorDescription getHubConnectorDescription() {
                    return failedConnection;
                }

                @Override
                public long getTimeStamp() {
                    return lastConnectionAttempt;
                }
            });
        }

        // remove attempts which where successful later on
        List<FailedConnectionAttempt> connectedNow = new ArrayList<>();
        for (HubConnectionManager.FailedConnectionAttempt failedAttempt : this.failedConnectionAttempts) {
            for (HubConnectorDescription runningConnection : this.hcdListHub) {
                if (failedAttempt.getHubConnectorDescription().isSame(runningConnection)) {
                    connectedNow.add(failedAttempt);
                }
            }
        }
        for (FailedConnectionAttempt failedAttempt : connectedNow) {
            this.failedConnectionAttempts.remove(failedAttempt);
        }
    }

    private HubConnectorDescription findSameInList(HubConnectorDescription hcd, List<HubConnectorDescription> hcdList) {
        for (HubConnectorDescription hcdInList : hcdList) {
            if (hcd.isSame(hcdInList)) {
                return hcdInList;
            }
        }
        return null;
    }

    @Override
    public void connectHub(HubConnectorDescription hcd) throws SharkException, IOException {
        this.lastConnectionAttempt = System.currentTimeMillis(); // new connection attempt
        // already connected?
        if (this.findSameInList(hcd, this.hcdList) != null) return; // yes, connected: nothing to do here
        // else
        this.hcdList.add(hcd);
    }


    @Override
    public void disconnectHub(HubConnectorDescription hcd) throws SharkException, IOException {
        // *not* in there?
        HubConnectorDescription disconnectHcd = this.findSameInList(hcd, this.hcdList);
        if (disconnectHcd != null) {
            this.hcdList.remove(disconnectHcd);
        } else {
            // remove hcd from failed attempts list
            FailedConnectionAttempt attemptToRemove = null;
            for (FailedConnectionAttempt failedAttempt : this.failedConnectionAttempts) {
                if(failedAttempt.getHubConnectorDescription().isSame(hcd)){
                    attemptToRemove = failedAttempt;
                    break;
                }
            }
            if (attemptToRemove != null) this.failedConnectionAttempts.remove(attemptToRemove);
        }


    }

    @Override
    public void disconnectHub(int index) throws SharkException, IOException {
        this.disconnectHub(this.hcdList.get(index));
    }

    @Override
    public List<HubConnectorDescription> getConnectedHubs() {
        this.syncLists();
        return this.hcdList; // return this list outside hub
    }

    @Override
    public List<HubConnectionManager.FailedConnectionAttempt> getFailedConnectionAttempts() {
        this.syncLists();
        return this.failedConnectionAttempts;
    }
}
