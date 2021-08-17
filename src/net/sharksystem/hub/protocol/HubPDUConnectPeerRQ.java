package net.sharksystem.hub.protocol;

import net.sharksystem.asap.ASAPException;
import net.sharksystem.asap.utils.ASAPSerialization;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class HubPDUConnectPeerRQ extends HubPDU {
    private final boolean newConnection;
    public CharSequence peerID;

    public HubPDUConnectPeerRQ(CharSequence peerID) {
        this(peerID, false);
    }

    public HubPDUConnectPeerRQ(CharSequence peerID, boolean newConnection) {
        super(CONNECT_PEER_REQUEST);
        this.peerID = peerID;
        this.newConnection = newConnection;
    }

    public HubPDUConnectPeerRQ(InputStream is) throws IOException, ASAPException {
        super(CONNECT_PEER_REQUEST);
        this.peerID = ASAPSerialization.readCharSequenceParameter(is);
        this.newConnection = ASAPSerialization.readBooleanParameter(is);
    }

    @Override
    public void sendPDU(OutputStream os) throws IOException {
        super.sendPDUNumber(os);
        ASAPSerialization.writeCharSequenceParameter(this.peerID, os);
        ASAPSerialization.writeBooleanParameter(this.newConnection, os);
    }

    public boolean getNewConnection() {
        return this.newConnection;
    }
}
