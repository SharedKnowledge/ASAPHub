package net.sharksystem.hub;

import net.sharksystem.hub.protocol.*;

/**
 * A connector reacts on received hub connector protocol data units (PDUs). There are two side:
 * hub and peer side. A peer registers with a peer but only unregisters by closing the connection.
 * <br/><br/>
 * A peer retrieves a set of registered peers on the hub by sending a status request. The hub will reply with
 * a status reply.
 * <br/><br/>
 * A peer ask for a connection to another peer by sending a connection request. The hub will try to initiate
 * a data connection to this peer. This attempt can take an unexpected period of time an can fail at all.
 * There are not actions required on peer side.
 * <br/><br/>
 * The hub initiates a data connection. The behaviour is different on a shared an non-shared channel.
 * We talk about a <i>shared channel</i> if the end-to-end data exchange between two peers uses the same
 * stream pair as the connector protocol.
 * <br/><br/>
 * <b>On a shared channel</b>, both connector engines must cease communication before launching a data connection. Both side must re-start
 * their work after data connection ended. This process is negotiated in some steps:
 * <br/>
 * <sl>
 *     <li>Hub send a silent request to the peer. Peer connect shall stop issuing PDUs to the hub.</li>
 *     <li>Peer side sends a silent response with a duration. It will not send any data during this period of time</li>
 *     <li>The hub sends a channel clear pdu as very last message with a time out duration.</li>
 *     <li>Both side will re-launch their connectors if no data are sent over the channel within that duration.</li>
 * </sl>
 *
 */
public interface Connector {
    /**
     * A silent request is sent from hub to peer side as the first step to initiate a connection on a shared
     * channel. Peer side is meant to stop sending further messages. Peer side sends an acknowledgment (silent reply)
     * if ready.
     * @param pdu
     */
    void silentRQ(HubPDUSilentRQ pdu);

    /**
     * Send from peer side to hub to confirm a silent channel. This pdu contains a time out period.
     * @param pdu
     */
    void silentRPLY(HubPDUSilentRPLY pdu);

    /**
     * On a shared channel implementation: This pdu is exchanged prior to a data connection. But connector engine
     * must stop sending but resume their work after data connection has ended.
     * <br/><br/>
     * A non-shared implementation can ignore this pdu.
     *
     * @param pdu
     */
    void channelClear(HubPDUChannelClear pdu);

    /**
     * This pdu is sent from peer to hub to register peer with its id. There is not unregister. A peer is
     * unregistered when connection is closed
     * @param pdu
     */
    void register(HubPDURegister pdu);

    /**
     * Send from peer to hub and asks for a data connection to a remote peer.
     * @param pdu
     */
    void connectPeerRQ(HubPDUConnectPeerRQ pdu);

    /**
     * Send from peer to hub and asks for status information, most importantly a set of registered peers
     * @param pdu
     */
    void hubStatusRQ(HubPDUHubStatusRQ pdu);

    /**
     * Send from hub to peer. Contains a set of registered peer id.
     * @param pdu
     */
    void hubStatusRPLY(HubPDUHubStatusRPLY pdu);

    /**
     * Called from connector engine - connection is closed
     * @param unregister
     */
    void connectorSessionEnded(boolean unregister);

    /**
     * called when the connector thread was created.
     * @param connectorThread
     */
    void connectorSessionStarted(ConnectorThread connectorThread);

    CharSequence getPeerID();

    /**
     * True if this connector runs on hub side - false if peer side
     * @return
     */
    boolean isHubSide();

    void unregister(HubPDUUnregister hubPDU);

    int DEFAULT_TIMEOUT_IN_MILLIS = 1000;
    void setTimeOutInMillis(int milliseconds);

    /**
     * Called from hub - asks peer side to open a new connection with given parameter to initiate a new peer encounter
     * @param hubPDU
     */
    void newConnectionReply(HubPDUConnectPeerNewConnectionRPLY hubPDU);

    /**
     * send from peer side to hub side. Asks to provide a new server socket to initiate a new peer encounter.
     * @param hubPDU
     */
    void newConnectionRequest(HubPDUConnectPeerNewTCPSocketRQ hubPDU);

    void notifyPDUReceived(HubPDU hubPDU);
}
