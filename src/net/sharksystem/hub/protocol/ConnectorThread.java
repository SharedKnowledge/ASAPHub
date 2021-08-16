package net.sharksystem.hub.protocol;

import net.sharksystem.asap.ASAPException;
import net.sharksystem.hub.Connector;
import net.sharksystem.utils.Log;

import java.io.IOException;
import java.io.InputStream;

/**
 * Thread that reads and writes connector PDUs. Actual application logic is delegated to a connector implementation.
 */
public class ConnectorThread extends Thread {
    private final Connector connector;
    private final InputStream is;
    private boolean again = true;

    public ConnectorThread(Connector connector, InputStream is) {
        this.connector = connector;
        this.is = is;
    }

    public void kill() {
        this.again = false;
    }

    public void run() {
        /* this thread can be shut down to give space to data stream. It can also break down
        in that case - connection got lost - maybe peer is to be unregistered..
         */
        boolean noRecovery = false;

        try {
            this.connector.connectorSessionStarted(this);
            Log.writeLog(this, this.toString(),"connector engine started");

            while (this.again) {
                HubPDU hubPDU = HubPDU.readPDU(this.is);

                this.connector.notifyPDUReceived(hubPDU);

                if (hubPDU instanceof HubPDUHubStatusRQ) {
                    Log.writeLog(this, this.toString(), "read hub status RQ");
                    this.connector.hubStatusRQ((HubPDUHubStatusRQ) hubPDU);
                }
                else if (hubPDU instanceof HubPDUHubStatusRPLY) {
                    Log.writeLog(this, this.toString(), "read hub status RPLY");
                    this.connector.hubStatusRPLY((HubPDUHubStatusRPLY) hubPDU);
                }
                else if (hubPDU instanceof HubPDUSilentRQ) {
                    Log.writeLog(this, this.toString(), "read hub silent RQ");
                    this.connector.silentRQ((HubPDUSilentRQ) hubPDU);
                }
                else if (hubPDU instanceof HubPDUSilentRPLY) {
                    Log.writeLog(this, this.toString(), "read hub silent RPLY");
                    this.connector.silentRPLY((HubPDUSilentRPLY) hubPDU);
                }
                else if (hubPDU instanceof HubPDUChannelClear) {
                    Log.writeLog(this, this.toString(), "read hub channel clear");
                    this.connector.channelClear((HubPDUChannelClear) hubPDU);
                }
                else if (hubPDU instanceof HubPDURegister) {
                    Log.writeLog(this, this.toString(), "read hub register");
                    this.connector.register((HubPDURegister) hubPDU);
                }
                else if (hubPDU instanceof HubPDUUnregister) {
                    Log.writeLog(this, this.toString(), "read hub unregister");
                    this.connector.unregister((HubPDUUnregister) hubPDU);
                }
                else if (hubPDU instanceof HubPDUConnectPeerRQ) {
                    Log.writeLog(this, this.toString(), "read hub connect peer RQ");
                    this.connector.connectPeerRQ((HubPDUConnectPeerRQ) hubPDU);
                }
                else if (hubPDU instanceof HubPDUConnectPeerNewTCPSocketRQ) {
                    Log.writeLog(this, this.toString(), "read hub new connection request");
                    this.connector.newConnectionRequest((HubPDUConnectPeerNewTCPSocketRQ) hubPDU);
                }
                else if (hubPDU instanceof HubPDUConnectPeerNewConnectionRPLY) {
                        Log.writeLog(this, this.toString(), "read hub new connection reply");
                        this.connector.newConnectionReply((HubPDUConnectPeerNewConnectionRPLY) hubPDU);
                } else {
                    Log.writeLog(this, this.toString(), "got unknown / unsupported PDU type: "
                        + hubPDU.getClass().getSimpleName());
                }
            }
        } catch (IOException | ASAPException e) {
            Log.writeLog(this, this.connector.toString(),"connection lost - no recovery expected");
            noRecovery = true; // connection lost
        } catch (ClassCastException e) {
            Log.writeLog(this, this.connector.toString(),"wrong pdu class - crazy: " + e.getLocalizedMessage());
        } finally {
            Log.writeLog(this, this.connector.toString(), "hub session ended");
            this.connector.connectorSessionEnded(noRecovery);
        }
    }

    public String toString() {
        return this.connector.toString();
    }
}
