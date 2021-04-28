package net.sharksystem.hub.peerside;

import net.sharksystem.asap.ASAPException;
import net.sharksystem.hub.protocol.HubPDU;
import net.sharksystem.hub.protocol.HubPDUConnectPeerNewConnectionRPLY;
import net.sharksystem.hub.protocol.HubPDUHubStatusRPLY;
import net.sharksystem.utils.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collection;

public class SharedStreamPairConnectorPeerSide extends HubConnectorImpl implements HubConnector {
    private final String hostName;
    private final int port;
    private NewConnectionListener listener;
    private Socket hubSocket;
    private Collection<CharSequence> peerIDs = new ArrayList<>();

    public static HubConnector createTCPHubConnector(CharSequence hostName, int port) throws IOException {
        // create TCP connection to hub
        Socket hubSocket = new Socket(hostName.toString(), port);

        return new SharedStreamPairConnectorPeerSide(hubSocket, hostName, port);
    }

    public SharedStreamPairConnectorPeerSide(Socket hubSocket, CharSequence hostName, int port) throws IOException {
        super(hubSocket.getInputStream(), hubSocket.getOutputStream());
        this.hubSocket = hubSocket;
        this.hostName = hostName.toString();
        this.port = port;
    }

    @Override
    public void disconnectHub() throws IOException {
        super.disconnectHub();
        this.hubSocket.close();
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////
    //                        TCP - edition: hub management protocol engine (connector side)           //
    ////////////////////////////////////////////////////////////////////////////////////////////////////

    // TODO..
    private class HubManagementProtocolEngineThread extends Thread {
        private final InputStream hubIS;
        private final OutputStream hubOS;
        private boolean killed;
        private Thread thread;

        public HubManagementProtocolEngineThread(InputStream hubIS, OutputStream hubOS) {
            this.hubIS = hubIS;
            this.hubOS = hubOS;
            this.killed = false;
        }

        public void run() {
            this.thread = Thread.currentThread();
            try {
                while(!this.killed) {
                    HubPDU hubPDU = HubPDU.readPDU(hubIS);
                    Log.writeLog(this, "read pdu from hub");
                    if (hubPDU instanceof HubPDUConnectPeerNewConnectionRPLY) {
                        Log.writeLog(this, "new connection pdu from hub");
                        HubPDUConnectPeerNewConnectionRPLY hubPDUConnectPeerNewConnectionRPLY = (HubPDUConnectPeerNewConnectionRPLY) hubPDU;
                        /*
                        if(AbstractHubConnector.this.listener != null) {
                            // create a connection
                            Socket socket = new Socket(AbstractHubConnector.this.hostName, newConnectionPDU.port);

                            // tell listener
                            AbstractHubConnector.this.listener.notifyPeerConnected(
                                    new PeerConnectionImpl(
                                            newConnectionPDU.peerID,
                                            socket.getInputStream(),
                                            socket.getOutputStream()));
                        }
                         */
                    } else if (hubPDU instanceof HubPDUHubStatusRPLY) {
                        Log.writeLog(this, "provide hub information pdu from hub");
                        HubPDUHubStatusRPLY hubPDUHubStatusRPLY = (HubPDUHubStatusRPLY) hubPDU;
//                        AbstractHubConnector.this.peerIDs = provideHubInfoPDU.connectedPeers;
                    } else {
                        Log.writeLog(this, "unknown PDU, give up");
                        break;
                    }
                }
            } catch (IOException | ASAPException e) {
                Log.writeLog(this, "connection lost to hub");

            } catch (ClassCastException e) {
                Log.writeLog(this, "wrong pdu class - crazy: " + e.getLocalizedMessage());
            }
        }

        public void kill() {
            this.killed = true;
            if(this.thread != null) {
                this.thread.interrupt();
            }
        }
    }

}
