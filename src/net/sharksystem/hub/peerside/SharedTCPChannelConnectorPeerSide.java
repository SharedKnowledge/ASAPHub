package net.sharksystem.hub.peerside;

import net.sharksystem.hub.ASAPHubException;
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

        Log.writeLog(this, "connected to hub: " + hostName + ":" + port);

        this.hubSocket = hubSocket;
        this.hostName = hostName.toString();
        this.port = port;
    }
}
