package net.sharksystem.hub;

import net.sharksystem.asap.ASAPException;
import net.sharksystem.asap.utils.ASAPSerialization;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

class HubPDUConnectPeerRQ extends HubPDU {
    public CharSequence peerID;

    public HubPDUConnectPeerRQ(CharSequence peerID) {
        super(CONNECT_PEER_REQUEST);
        this.peerID = peerID;
    }

    public HubPDUConnectPeerRQ(InputStream is) throws IOException, ASAPException {
        super(CONNECT_PEER_REQUEST);
        this.peerID = ASAPSerialization.readCharSequenceParameter(is);
    }

    @Override
    void sendPDU(OutputStream os) throws IOException {
        super.sendPDUNumber(os);
        ASAPSerialization.writeCharSequenceParameter(this.peerID, os);
    }
}
