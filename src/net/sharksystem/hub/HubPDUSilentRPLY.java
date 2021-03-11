package net.sharksystem.hub;

import net.sharksystem.asap.ASAPException;
import net.sharksystem.asap.utils.ASAPSerialization;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class HubPDUSilentRPLY extends HubPDUSilent {
    public HubPDUSilentRPLY(long waitInMillis) {
        super(HubPDU.SILENT_REPLY, waitInMillis);
    }

    public HubPDUSilentRPLY(InputStream is) throws IOException, ASAPException {
        super(HubPDU.SILENT_REPLY, is);
    }
}
