package net.sharksystem.hub.protocol;

import net.sharksystem.asap.ASAPException;
import net.sharksystem.asap.utils.ASAPSerialization;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class HubPDUUnregister extends HubPDU {
    public final CharSequence peerID;

    public HubPDUUnregister(CharSequence peerID) {
        super(HUB_UNREGISTER);
        this.peerID = peerID;
    }

    HubPDUUnregister(InputStream is) throws IOException, ASAPException {
        super(HUB_UNREGISTER);
        this.peerID = ASAPSerialization.readCharSequenceParameter(is);
    }

    @Override
    public void sendPDU(OutputStream os) throws IOException {
        super.sendPDUNumber(os);
        ASAPSerialization.writeCharSequenceParameter(this.peerID, os);
    }

}
