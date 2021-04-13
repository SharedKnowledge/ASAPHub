package net.sharksystem.hub;

import net.sharksystem.asap.ASAPException;

import java.io.IOException;
import java.util.Set;

/**
 * Internal hub interface. It is meant to be used by HubSessions.
 * @author Thomas Schwotzer
 */
public interface HubInternal {
    /**
     * Is a peer already connected to this hub? Do not confuse connection to the hub with
     * a connection within a hub.
     * @param peerID
     * @return true - there is already a connection from a peer to this hub
     */
    boolean isRegistered(CharSequence peerID);

    /**
     * Get peer ids who are connected with the hub
     * @return
     */
    Set<CharSequence> getRegisteredPeerIDs();

    void sessionStarted(CharSequence peerID, HubSession hubSession);

    void sessionEnded(CharSequence peerID, HubSession hubSession);

    /**
     * Ask hub to create a new connection to a peer within the hub. Hub calls openDataSession in return
     * @param peerID target peer id
     * @return open connection
     * @see HubSession#openDataSession(StreamPair)
     */
    void connectionRequest(CharSequence peerID, HubSession hubSession) throws ASAPException, IOException;

    void notifySilent(HubSession hubSession);

    long getMaxIdleInMillis();
}
