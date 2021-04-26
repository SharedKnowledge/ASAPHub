package net.sharksystem.hub.protocol;

import net.sharksystem.asap.ASAPException;
import net.sharksystem.asap.utils.ASAPSerialization;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class HubPDUConnectPeerNewTCPSocketRQ extends HubPDU {
    public CharSequence peerID;

    public HubPDUConnectPeerNewTCPSocketRQ(CharSequence peerID) {
        super(CONNECT_PEER_NEW_TCP_SOCKET_REQUEST);
        this.peerID = peerID;
    }

    public HubPDUConnectPeerNewTCPSocketRQ(InputStream is) throws IOException, ASAPException {
        super(CONNECT_PEER_NEW_TCP_SOCKET_REQUEST);
        this.peerID = ASAPSerialization.readCharSequenceParameter(is);
    }

    @Override
    public void sendPDU(OutputStream os) throws IOException {
        super.sendPDUNumber(os);
        ASAPSerialization.writeCharSequenceParameter(this.peerID, os);
    }

}
