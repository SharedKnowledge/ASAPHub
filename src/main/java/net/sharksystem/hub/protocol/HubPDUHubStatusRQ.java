package net.sharksystem.hub.protocol;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class HubPDUHubStatusRQ extends HubPDU {
    public HubPDUHubStatusRQ(InputStream is) {
        super(HUB_STATUS_REQUEST);
    }

    public HubPDUHubStatusRQ() {
        super(HUB_STATUS_REQUEST);
    }

    @Override
    public void sendPDU(OutputStream os) throws IOException {
        super.sendPDUNumber(os);
    }
}
