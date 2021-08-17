package net.sharksystem.hub.hubside;

import net.sharksystem.asap.ASAPException;
import net.sharksystem.hub.ASAPHubException;
import net.sharksystem.hub.protocol.ConnectionRequest;
import net.sharksystem.hub.protocol.HubPDUConnectPeerNewTCPSocketRQ;
import net.sharksystem.streams.StreamPair;
import net.sharksystem.utils.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;

public class MultipleTCPChannelsConnectorHubSideImpl extends SharedChannelConnectorHubSideImpl
        implements NewConnectionCreatorListener {

    public MultipleTCPChannelsConnectorHubSideImpl(InputStream is, OutputStream os, HubInternal hub)
            throws ASAPException {

        super(is, os, hub);
    }

    @Override
    public boolean canEstablishTCPConnections() {
        return true;
    }

    protected boolean initDataSessionOnNewConnection(ConnectionRequest connectionRequest,
                                 int timeOutConnectionRequest, int timeOutDataConnection) throws IOException {
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
        (new NewConnectionCreator(srvSocket, this, connectionRequest,
                timeOutConnectionRequest, timeOutDataConnection)).start();

        // tell peer side connector to connect to server socket
        HubPDUConnectPeerNewTCPSocketRQ newConnectionRQ = new HubPDUConnectPeerNewTCPSocketRQ(
                connectionRequest.targetPeerID, localPort);

        Log.writeLog(this, "ask my peer to connect to port: " + localPort);
        newConnectionRQ.sendPDU(this.getOutputStream());

        return true;
    }

    // called as result of a new connection request - peer has created a new data connection
    @Override
    public void connectionCreated(CharSequence sourcePeerID, CharSequence targetPeerID, StreamPair streamPair) {
        // tell hub - we have a new data connection and are eager to be connected with other peer
        try {
            this.getHub().startDataSession(sourcePeerID, targetPeerID, streamPair, this.getTimeOutDataConnection());
        } catch (ASAPHubException | IOException e) {
            Log.writeLog(this,"could not create data connection: " + e.getLocalizedMessage());
        }
    }
}
