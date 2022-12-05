package net.sharksystem.hub.peerside;

import net.sharksystem.asap.ASAPException;
import net.sharksystem.hub.ASAPHubException;
import net.sharksystem.hub.protocol.ConnectionRequest;
import net.sharksystem.hub.protocol.HubPDU;
import net.sharksystem.hub.protocol.HubPDUConnectPeerNewTCPSocketRQ;
import net.sharksystem.streams.StreamPairImpl;
import net.sharksystem.utils.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

import static net.sharksystem.hub.peerside.HubConnectorDescription.TCP;

public class SharedTCPChannelConnectorPeerSide extends SharedChannelConnectorPeerSide {
    private final String hostName;
    private final int port;
    private final boolean multiChannel;
    private Socket hubSocket;

    public static HubConnector createTCPHubConnector(CharSequence hostName, int port)
            throws IOException, ASAPHubException {

        return createTCPHubConnector(hostName, port, true);
    }

    public static HubConnector createTCPHubConnector(CharSequence hostName, int port, boolean multiChannel)
        throws IOException, ASAPHubException {
        // create TCP connection to hub
        Socket hubSocket = new Socket(hostName.toString(), port);

        return new SharedTCPChannelConnectorPeerSide(hubSocket, hostName, port, multiChannel);
    }

    public SharedTCPChannelConnectorPeerSide(Socket hubSocket, CharSequence hostName, int port, boolean multiChannel)
            throws IOException, ASAPHubException {
        super(hubSocket.getInputStream(), hubSocket.getOutputStream());

        Log.writeLog(this, this.toString(),"connected to hub: " + hostName + ":" + port);

        this.hubSocket = hubSocket;
        this.hostName = hostName.toString();
        this.port = port;
        this.multiChannel = multiChannel;
    }

    // overwrite default
    public void connectHub(CharSequence localPeerID) throws IOException, ASAPException {
        this.connectHub(localPeerID, this.multiChannel);
    }

    @Override
    public boolean isSame(HubConnectorDescription hcd) {
        if(hcd.getType() != TCP) {
            return false;
        }

        try {
            if (!hcd.getHostName().toString().equalsIgnoreCase(this.hostName)) {
                return false;
            }
            if (hcd.getPortNumber() != this.port) {
                return false;
            }
        }
        catch(ASAPHubException e) {
            // definitive no TCP connector description
            return false;
        }

        // no difference found
        return true;
    }

    @Override
    public void openNewTCPConnectionRequest(HubPDUConnectPeerNewTCPSocketRQ pdu) {
        if(!this.multiChannel) {
            Log.writeLog(this, this.toString(),"no multiChannel support " + pdu.peerID);
            this.pduNotHandled(pdu);
            return;
        }

        // else - multiChannel
        Log.writeLog(this, this.toString(),"asked to open a new connection to " + pdu.peerID);

        try {
            Socket newPeerSocket = new Socket(this.hostName, pdu.getPort());
            Log.writeLog(this, this.toString(), "connected - wait clearance message");
            new Wait4Clear(pdu.peerID, newPeerSocket).start();
        } catch (IOException e) {
            Log.writeLog(this, this.toString(),"could not establish new TCP connection for new peer encounter");
        }

    }
    private class Wait4Clear extends Thread {
        private final InputStream is;
        private final CharSequence peerID;
        private final OutputStream os;

        Wait4Clear(CharSequence peerID, Socket newSocket) throws IOException {
            this.is = newSocket.getInputStream();
            this.os = newSocket.getOutputStream();
            this.peerID = peerID;
        }

        public final void run() {
            /*
            try {
                this.is.read(); // block to wait until connection is linked on hub side
            } catch (IOException e) {
                Log.writeLog(this, this.toString(), "newly created socket died before first usage");
                return;
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                // ignore
            }
             */
            String s = SharedTCPChannelConnectorPeerSide.this.getPeerID() + " --> " + this.peerID;
            SharedTCPChannelConnectorPeerSide.this.dataSessionStarted(
                    this.peerID, StreamPairImpl.getStreamPairWithSessionID(this.is, this.os, s));
        }
    }

    public String toString() {
        return this.getPeerID() + " | " + this.hostName + ":" + this.port;
    }
}
