package net.sharksystem.hub;

import net.sharksystem.asap.ASAPException;
import net.sharksystem.asap.utils.ASAPSerialization;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

class HubPDUConnectPeerNewConnectionRPLY extends HubPDU {
    CharSequence peerID;
    int port;

    public HubPDUConnectPeerNewConnectionRPLY(InputStream is) throws IOException, ASAPException {
        super(CONNECT_PEER_REPLY);
        this.port = ASAPSerialization.readIntegerParameter(is);
        this.peerID = ASAPSerialization.readCharSequenceParameter(is);
    }

    public HubPDUConnectPeerNewConnectionRPLY(int port, CharSequence peerID) {
        super(CONNECT_PEER_REPLY);
        this.port = port;
        this.peerID = peerID;
    }

    @Override
    void sendPDU(OutputStream os) throws IOException {
        super.sendPDUNumber(os);
        ASAPSerialization.writeIntegerParameter(this.port, os);
        ASAPSerialization.writeCharSequenceParameter(this.peerID, os);
    }
}
