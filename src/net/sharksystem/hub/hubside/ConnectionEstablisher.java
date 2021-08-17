package net.sharksystem.hub.hubside;

import net.sharksystem.hub.ASAPHubException;
import net.sharksystem.streams.StreamPair;

import java.io.IOException;

/**
 * Creating a data session over a hub between two peers involves at least three but more likely six different threads.
 * Other threads might intervene. Let's paint the whole picture:
 * <br/><br/>
 * We have two peers Alice and Bob running on different machines. Each peer uses a connector to communicate with
 * a hub. (Both connectors run on different machines, different processes). There is a HubConnectionSession object for
 * each connector on the hub (additional two threads).
 * <br/><br/>
 * The hub itself could be distributed itself which adds at least two additional threads to the system. And this is only
 * when two peers are active.
 * <br/><br/>
 * Let's discuss connection establishment and avoiding deadlocks. (Spoiler: We could incidentally create a starving
 * philosopher dilemma): Alice peer want's to communicate with Bob peer. Alice connector asks hub via connector to
 * establish a connection. This question must not block Alice peer process or the corresponding
 * HubConnectorSession thread (for ever). A HubConnectorSession on Bobs' side must be found. Hub would relay this
 * request to Bobs' side which can take a while. It could also be extremely fast if a the hub is not distributed.
 * <br/><br/>
 * In any case, Bob could be busy, e.g. exchanging data with Clara. Here is the point: Alice must not wait for a
 * connection to Bob. If so, Bob would also wait, let's say for Clara who might wait for Alice... Deadlock. We need
 * to work with time outs.
 * <br/><br/>
 * Let's assume, Bob is not busy. He knows Alice time out and could decide to block itself and offer an open stream pair
 * to exchange data with Alice. Bob starts its own clock.
 * <br/><br/>
 * Alice would receive a message that Bob not only willing to talk but has already an open connection. Alice could
 * do the same and start a session. Now, we have an end-to-end connection. This connection itself is under guard of
 * another time out: It is teared down if no data are transmitted over a defined period of time.
 * <br/><br/>
 * This interface must be supported by hub and hub session. In our example:
 * Alice HubConnectionSession (A_hcs) calls connectionRequest("Bob", timeout) Hub on Alice (A_hub) side.
 * <br/><br/>
 * A_hub would tell Bobs hub (in case of distributed hubs). B_hub knows this call is from Alice.
 * <br/><br/>
 * B_hub -> b_hcs.connectionRequest("Alice", timeout); // assume Bob is not busy - creates an open stream pair.
 * <br/>
 * B_hcs -> b_hub.startDataSession("Alice", streamPair object, timeout); // this goes back to A_hub, Alice knows it is from Bob
 * <br/>
 * A_hub -> a_hcs.startDataSession("Bob", streamPair object, timeout); // if not timed out - connection established
 *
 * @author Thomas Schwotzer
 */
public interface ConnectionEstablisher {
    /**
     * Ask for a connection to another peer. This request will be valid until it was withdrawn.
     * @param targetPeerID peer ID to which a communication is to be established
     * @throws ASAPHubException if there is no such peer registered
     */
    void connectionRequest(CharSequence sourcePeerID, CharSequence targetPeerID, int timeout)
            throws ASAPHubException, IOException;

    void connectionRequest(CharSequence sourcePeerID, CharSequence targetPeerID, int timeout, boolean newConnection)
            throws ASAPHubException, IOException;

    /**
     * An existing connection is terminated. Connection requests are discarded. Nothing happens otherwise.
     * @param targetPeerID
     * @throws ASAPHubException if no such peer id exists.
     */
    void disconnect(CharSequence sourcePeerID, CharSequence targetPeerID) throws ASAPHubException;

    /**
     * Caller is ready to communicate with target peer. This connection will remain open for a number of seconds.
     * @param connection connection that is to be used for data exchange
     * @param timeout in milliseconds
     * @throws ASAPHubException unknown peer id, no previous connection request
     */
    void startDataSession(CharSequence sourcePeerID, CharSequence targetPeerID, StreamPair connection, int timeout) throws ASAPHubException, IOException;

    /**
     * A data session is terminated after a time out and can be closed. This methods allows terminating a data session
     * for other reasons.
     *
     * @param targetPeerID
     * @param connection
     * @throws ASAPHubException implementations can use it fits.
     */
    void notifyConnectionEnded(CharSequence sourcePeerID, CharSequence targetPeerID, StreamPair connection) throws ASAPHubException;
}
