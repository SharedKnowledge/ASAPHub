package net.sharksystem.hub;

import net.sharksystem.asap.ASAPException;
import net.sharksystem.asap.utils.ASAPSerialization;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

abstract class HubPDUSilent extends HubPDU {
    long waitDuration;

    public HubPDUSilent(byte pduNumber, long waitDuration) {
        super(pduNumber);
        this.waitDuration = waitDuration;
    }

    public HubPDUSilent(byte pduNumber, InputStream is) throws IOException, ASAPException {
        super(pduNumber);
        this.waitDuration = ASAPSerialization.readLongParameter(is);
    }

    @Override
    void sendPDU(OutputStream os) throws IOException {
        super.sendPDUNumber(os);
        ASAPSerialization.writeLongParameter(this.waitDuration, os);
    }
}