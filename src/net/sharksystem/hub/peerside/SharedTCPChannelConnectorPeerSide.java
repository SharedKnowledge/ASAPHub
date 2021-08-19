package net.sharksystem.hub.peerside;

import net.sharksystem.hub.ASAPHubException;
import net.sharksystem.hub.protocol.ConnectionRequest;
import net.sharksystem.hub.protocol.HubPDU;
import net.sharksystem.hub.protocol.HubPDUConnectPeerNewTCPSocketRQ;
import net.sharksystem.streams.StreamPairImpl;
import net.sharksystem.utils.Log;

import java.io.IOException;
import java.net.Socket;

public class SharedTCPChannelConnectorPeerSide extends SharedChannelConnectorPeerSide {
    private final String hostName;
    private final int port;
    private Socket hubSocket;

    public static HubConnector createTCPHubConnector(CharSequence hostName, int port)
            throws IOException, ASAPHubException {

        // create TCP connection to hub
        Socket hubSocket = new Socket(hostName.toString(), port);

        return new SharedTCPChannelConnectorPeerSide(hubSocket, hostName, port);
    }

    public SharedTCPChannelConnectorPeerSide(Socket hubSocket, CharSequence hostName, int port)
            throws IOException, ASAPHubException {
        super(hubSocket.getInputStream(), hubSocket.getOutputStream());

        Log.writeLog(this, this.toString(),"connected to hub: " + hostName + ":" + port);

        this.hubSocket = hubSocket;
        this.hostName = hostName.toString();
        this.port = port;
    }

    @Override
    public void openNewTCPConnectionRequest(HubPDUConnectPeerNewTCPSocketRQ pdu) {
        Log.writeLog(this, this.toString(),"asked to open a new connection to " + pdu.peerID);

        try {
            Socket newPeerSocket = new Socket(this.hostName, pdu.getPort());
            Log.writeLog(this, this.toString(), "connected - wait for clear message");
            //HubPDU hubPDU = HubPDU.readPDU(newPeerSocket.getInputStream());
            this.dataSessionStarted(pdu.getPeerID(),
                    StreamPairImpl.getStreamPair(newPeerSocket.getInputStream(), newPeerSocket.getOutputStream()));
        } catch (IOException e) {
            Log.writeLog(this, this.toString(),"could not establish new TCP connection for new peer encounter");
        }

    }

    /*
    private class Wait4Clear extends Thread {
        Wait4Clear(InputStream is) {}
    }
     */

    public String toString() {
        return this.getPeerID() + " | " + this.hostName + ":" + this.port;
    }
}
