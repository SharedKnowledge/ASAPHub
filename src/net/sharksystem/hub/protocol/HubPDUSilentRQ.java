package net.sharksystem.hub.protocol;

import net.sharksystem.asap.ASAPException;

import java.io.IOException;
import java.io.InputStream;

public class HubPDUSilentRQ extends HubPDUSilent {
    public HubPDUSilentRQ(long waitInMillis) {
        super(SILENT_REQUEST, waitInMillis);
    }

    public HubPDUSilentRQ(InputStream is) throws IOException, ASAPException {
        super(SILENT_REQUEST, is);
    }
}