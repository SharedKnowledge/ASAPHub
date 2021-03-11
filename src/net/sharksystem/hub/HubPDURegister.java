package net.sharksystem.hub;

import net.sharksystem.asap.ASAPException;
import net.sharksystem.asap.utils.ASAPSerialization;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

class HubPDURegister extends HubPDU {
    final CharSequence peerID;

    HubPDURegister(CharSequence peerID) {
        super(HUB_REGISTER);
        this.peerID = peerID;
    }

    HubPDURegister(InputStream is) throws IOException, ASAPException {
        super(HUB_REGISTER);
        this.peerID = ASAPSerialization.readCharSequenceParameter(is);
    }

    @Override
    void sendPDU(OutputStream os) throws IOException {
        super.sendPDUNumber(os);
        ASAPSerialization.writeCharSequenceParameter(this.peerID, os);
    }
}
