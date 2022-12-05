package net.sharksystem.hub.protocol;

import net.sharksystem.asap.ASAPException;

import java.io.IOException;
import java.io.InputStream;

public class HubPDUSilentRPLY extends HubPDUSilent {
    public HubPDUSilentRPLY(long waitInMillis) {
        super(HubPDU.SILENT_REPLY, waitInMillis);
    }

    public HubPDUSilentRPLY(InputStream is) throws IOException, ASAPException {
        super(HubPDU.SILENT_REPLY, is);
    }
}
