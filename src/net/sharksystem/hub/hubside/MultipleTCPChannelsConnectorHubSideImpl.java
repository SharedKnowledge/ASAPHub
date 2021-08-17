package net.sharksystem.hub.hubside;

import net.sharksystem.asap.ASAPException;
import net.sharksystem.hub.protocol.ConnectionRequest;
import net.sharksystem.hub.protocol.HubPDUConnectPeerNewTCPSocketRQ;
import net.sharksystem.utils.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;

public class MultipleTCPChannelsConnectorHubSideImpl extends SharedChannelConnectorHubSideImpl {
    public MultipleTCPChannelsConnectorHubSideImpl(InputStream is, OutputStream os, HubInternal hub) throws ASAPException {
        super(is, os, hub);
    }

    protected boolean initDataSessionOnNewConnection(ConnectionRequest connectionRequest) throws IOException {
        // open a server socket - set alarm

        // now it gets messy - needs to be cleaned up sometimes
        HubInternal hub = this.getHub();
        if(!(hub instanceof TCPHub)) {
            Log.writeLog(this, "need TCPHub to work - FATAL");
            return false;
        }

        TCPHub tcpHub = (TCPHub) hub;
        ServerSocket srvSocket = null;
        try {
            srvSocket = tcpHub.getServerSocket();
        } catch (IOException e) {
            Log.writeLog(this, "failed to get a new server socket");
            return false;
        }

        int localPort = srvSocket.getLocalPort();

        // tell peer side connector to connect to server socket
        HubPDUConnectPeerNewTCPSocketRQ newConnectionRQ = new HubPDUConnectPeerNewTCPSocketRQ(
                connectionRequest.targetPeerID, localPort);

        Log.writeLog(this, "ask peer connector to connect to port: " + localPort);
        newConnectionRQ.sendPDU(this.getOutputStream());

        return true;
    }
}
