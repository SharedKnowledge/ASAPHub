package net.sharksystem.hub;

import net.sharksystem.asap.ASAPException;
import net.sharksystem.asap.utils.ASAPSerialization;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

abstract class HubConnectionPDU extends HubPDU {
    final CharSequence sourcePeerID;
    final CharSequence targetPeerID;

    HubConnectionPDU(byte pduNumber, CharSequence sourcePeerID, CharSequence targetPeerID) {
        super(pduNumber);
        this.sourcePeerID = sourcePeerID;
        this.targetPeerID = targetPeerID;
    }

    public HubConnectionPDU(byte pduNumber, InputStream is) throws IOException, ASAPException {
        super(pduNumber);
        this.sourcePeerID = ASAPSerialization.readCharSequenceParameter(is);
        this.targetPeerID = ASAPSerialization.readCharSequenceParameter(is);
    }

    protected void sendFromTo(OutputStream os) throws IOException {
        ASAPSerialization.writeCharSequenceParameter(this.sourcePeerID, os);
        ASAPSerialization.writeCharSequenceParameter(this.targetPeerID, os);
    }
}
