package net.sharksystem.hub.protocol;

import net.sharksystem.asap.ASAPException;
import net.sharksystem.asap.utils.ASAPSerialization;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class HubPDURegister extends HubPDU {
    public final CharSequence peerID;
    public final boolean canCreateTCPConnections;

    public HubPDURegister(CharSequence peerID, boolean canCreateTCPConnections) {
        super(HUB_REGISTER);
        this.peerID = peerID;
        this.canCreateTCPConnections = canCreateTCPConnections;
    }

    public HubPDURegister(InputStream is) throws IOException, ASAPException {
        super(HUB_REGISTER);
        this.peerID = ASAPSerialization.readCharSequenceParameter(is);
        this.canCreateTCPConnections = ASAPSerialization.readBooleanParameter(is);
    }

    @Override
    public void sendPDU(OutputStream os) throws IOException {
        super.sendPDUNumber(os);
        ASAPSerialization.writeCharSequenceParameter(this.peerID, os);
        ASAPSerialization.writeBooleanParameter(this.canCreateTCPConnections, os);
    }
}
