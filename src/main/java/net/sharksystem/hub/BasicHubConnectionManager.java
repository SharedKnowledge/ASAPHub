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
 *
 * Instances of that hub connection manager class are initiated with that encounter manager and an ASAPPeer instance.
 *
 * It provides just a few methods to connect and disconnect hubs. Some optional behaviour is hidden.
 */
public abstract class BasicHubConnectionManager implements HubConnectionManager {
    protected long lastConnectionAttempt = System.currentTimeMillis();

    // list outside hub - it can run out of sync with internal list in hub due to thread, connection losses etc.
    protected List<HubConnectorDescription> hcdList = new ArrayList<>();
    protected List<HubConnectorDescription> hcdListHub = new ArrayList<>();
    protected List<HubConnectionManager.FailedConnectionAttempt> failedConnectionAttempts = new ArrayList<>();

    /**
     * Hub manager can be asked to connect to or disconnect from hubs. We send a list of hubs which are to be
     * connected. Connection establishment can take a while, though. It is a wish list on application side for a while.
     * Connection establishment can fail - not any wish can be fulfilled.
     *
     * We assume that this methode is called due to user interaction. Meaning: Intervals between two calls are long
     * enough to establish a connection or fail in the attempt. Implication: We assume: hub internal list is always
     * accurate. We can keep track of failed attempts. And we do.
     */
    protected void syncLists() {
        // ask for current list from hub
        List<HubConnectorDescription> toBeRemoved = new ArrayList<>();
        for(HubConnectorDescription wishedConnection : this.hcdList) {
            boolean found = false;
            for(HubConnectorDescription runningConnection : this.hcdListHub) {
                if(wishedConnection.isSame(runningConnection)) {
                    found = true;
                    break; // found - go ahead.
                }
            }
            if(!found) toBeRemoved.add(wishedConnection);
        }

        // remove and remember unfulfilled wishes
        for(HubConnectorDescription failedConnection : toBeRemoved) {
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
            if(recordedOldAttempt != null) this.failedConnectionAttempts.remove(recordedOldAttempt);
            this.failedConnectionAttempts.add(new HubConnectionManager.FailedConnectionAttempt() {
                @Override
                public HubConnectorDescription getHubConnectorDescription() { return failedConnection; }
                @Override
                public long getTimeStamp() { return lastConnectionAttempt; }
            });
        }
    }

    private HubConnectorDescription findSameInList(HubConnectorDescription hcd, List<HubConnectorDescription> hcdList) {
        for(HubConnectorDescription hcdInList : hcdList) {
            if(hcd.isSame(hcdInList)) {
                return hcdInList;
            }
        }
        return null;
    }

    @Override
    public void connectHub(HubConnectorDescription hcd) throws SharkException, IOException {
        this.lastConnectionAttempt = System.currentTimeMillis(); // new connection attempt
        // already connected?
        if(this.findSameInList(hcd, this.hcdList) != null) return; // yes, connected: nothing to do here
        // else
        this.hcdList.add(hcd);
    }


    @Override
    public void disconnectHub(HubConnectorDescription hcd) throws SharkException, IOException {
        // *not* in there?
        HubConnectorDescription disconnectHcd = this.findSameInList(hcd, this.hcdList);
        if(disconnectHcd == null) return; // not connected - nothing to do here
        // else
        this.hcdList.remove(disconnectHcd);
        // sync hub connections
    }

    @Override
    public void disconnectHub(int index) throws SharkException, IOException {
        this.disconnectHub(this.hcdList.get(index));
    }

    @Override
    public List<HubConnectorDescription> getConnectedHubs() {
        return this.hcdList; // return this list outside hub
    }

    @Override
    public List<HubConnectionManager.FailedConnectionAttempt> getFailedConnectionAttempts() {
        return this.failedConnectionAttempts;
    }
}
