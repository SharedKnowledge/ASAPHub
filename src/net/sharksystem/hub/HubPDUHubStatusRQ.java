package net.sharksystem.hub;

import net.sharksystem.asap.utils.ASAPSerialization;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

class HubPDUHubStatusRQ extends HubPDU {
    public HubPDUHubStatusRQ(InputStream is) {
        super(HUB_STATUS_REQUEST);
    }

    public HubPDUHubStatusRQ() {
        super(HUB_STATUS_REQUEST);
    }

    @Override
    void sendPDU(OutputStream os) throws IOException {
        super.sendPDUNumber(os);
    }
}
