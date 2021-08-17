package net.sharksystem.hub.hubside;

import net.sharksystem.asap.ASAPException;
import net.sharksystem.hub.ASAPHubException;
import net.sharksystem.hub.protocol.ConnectionRequest;
import net.sharksystem.hub.protocol.HubPDUConnectPeerNewTCPSocketRQ;
import net.sharksystem.streams.StreamPairImpl;
import net.sharksystem.utils.AlarmClock;
import net.sharksystem.utils.AlarmClockListener;
import net.sharksystem.utils.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;

public class MultipleTCPChannelsConnectorHubSideImpl extends SharedChannelConnectorHubSideImpl {
    public MultipleTCPChannelsConnectorHubSideImpl(InputStream is, OutputStream os, HubInternal hub) throws ASAPException {
        super(is, os, hub);
    }

    @Override
    public boolean canEstablishTCPConnections() {
        return true;
    }

    protected boolean initDataSessionOnNewConnection(ConnectionRequest connectionRequest,
                                 int timeOutConnectionRequest, int timeOutDataConnection) throws IOException {
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
        (new WaitForConnectionThread(srvSocket, hub, connectionRequest,
                timeOutConnectionRequest, timeOutDataConnection)).start();

        // tell peer side connector to connect to server socket
        HubPDUConnectPeerNewTCPSocketRQ newConnectionRQ = new HubPDUConnectPeerNewTCPSocketRQ(
                connectionRequest.targetPeerID, localPort);

        Log.writeLog(this, "ask my peer to connect to port: " + localPort);
        newConnectionRQ.sendPDU(this.getOutputStream());

        return true;
    }

    class WaitForConnectionThread extends Thread implements AlarmClockListener {
        private final ServerSocket srv;
        private final HubInternal hub;
        private final ConnectionRequest connectionRequest;
        private final int timeOutConnectionRequest;
        private final int timeOutDataConnection;

        WaitForConnectionThread(ServerSocket srv, HubInternal hub, ConnectionRequest connectionRequest,
                                int timeOutConnectionRequest, int timeOutDataConnection) {
            this.srv = srv;
            this.hub = hub;
            this.connectionRequest = connectionRequest;
            this.timeOutConnectionRequest = timeOutConnectionRequest;
            this.timeOutDataConnection = timeOutDataConnection;
        }

        public void run() {
            try {
                // set alarm
                AlarmClock alarmClock = new AlarmClock(this.timeOutConnectionRequest, this);
                alarmClock.start();
                Socket newSocket = this.srv.accept();
                alarmClock.kill();
                Log.writeLog(this, "new connection initiated from peer side - setup data connection");

                this.hub.startDataSession(
                        this.connectionRequest.targetPeerID, // twisted: source becomes target and vice versa
                        this.connectionRequest.sourcePeerID,
                        StreamPairImpl.getStreamPair(newSocket.getInputStream(), newSocket.getOutputStream()),
                        this.timeOutDataConnection);

            } catch (IOException | ASAPHubException e) {
                // maybe time out killed server socket.
                Log.writeLog(this, "accept failed: " + e.getLocalizedMessage());
            }

        }

        @Override
        public void alarmClockRinging(int i) {
            Log.writeLog(this, "timeout - close server port");
            try {
                this.srv.close();
            } catch (IOException e) {
                Log.writeLog(this, "problems closing server socket: " + e.getLocalizedMessage());
            }
        }
    }
}
