package net.sharksystem.hub.protocol;

import net.sharksystem.asap.ASAPException;
import net.sharksystem.asap.utils.ASAPSerialization;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class HubPDUChannelClear extends HubConnectionPDU {
    public final long maxIdleInMillis;
    public final byte[] syncSequence;

    public HubPDUChannelClear(CharSequence sourcePeerID, CharSequence targetPeerID,
                              long maxIdleInMillis, byte[] syncSequence) {
        super(HubPDU.CHANNEL_CLEAR, sourcePeerID, targetPeerID);
        this.maxIdleInMillis = maxIdleInMillis;
        this.syncSequence = syncSequence;
    }

    public HubPDUChannelClear(InputStream is) throws IOException, ASAPException {
        super(HubPDU.CHANNEL_CLEAR, is);
        this.maxIdleInMillis = ASAPSerialization.readLongParameter(is);
        this.syncSequence = ASAPSerialization.readByteArray(is);
    }

    @Override
    public void sendPDU(OutputStream os) throws IOException {
        super.sendPDUNumber(os);
        super.sendFromTo(os);
        ASAPSerialization.writeLongParameter(this.maxIdleInMillis, os);
        ASAPSerialization.writeByteArray(this.syncSequence, os);
    }
}
