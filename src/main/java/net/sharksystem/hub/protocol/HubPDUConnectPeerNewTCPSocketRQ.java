package net.sharksystem.hub.protocol;

import net.sharksystem.asap.ASAPException;
import net.sharksystem.asap.utils.ASAPSerialization;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class HubPDUConnectPeerNewTCPSocketRQ extends HubPDU {
    private final int port;
    public CharSequence peerID;

    public HubPDUConnectPeerNewTCPSocketRQ(CharSequence peerID, int port) {
        super(OPEN_NEW_TCP_SOCKET_RQ);
        this.peerID = peerID;
        this.port = port;
    }

    public HubPDUConnectPeerNewTCPSocketRQ(InputStream is) throws IOException, ASAPException {
        super(OPEN_NEW_TCP_SOCKET_RQ);
        this.peerID = ASAPSerialization.readCharSequenceParameter(is);
        this.port = ASAPSerialization.readIntegerParameter(is);
    }

    @Override
    public void sendPDU(OutputStream os) throws IOException {
        super.sendPDUNumber(os);
        ASAPSerialization.writeCharSequenceParameter(this.peerID, os);
        ASAPSerialization.writeIntegerParameter(this.port, os);
    }

    public int getPort() { return this.port;}
    public CharSequence getPeerID() { return this.peerID;}

}
