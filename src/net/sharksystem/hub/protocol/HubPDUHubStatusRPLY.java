package net.sharksystem.hub.protocol;

import net.sharksystem.asap.ASAPException;
import net.sharksystem.asap.utils.ASAPSerialization;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Set;

public class HubPDUHubStatusRPLY extends HubPDU {
    public Set<CharSequence> connectedPeers;

    public HubPDUHubStatusRPLY(Set<CharSequence> connectedPeers) {
        super(HUB_STATUS_REPLY);
        this.connectedPeers = connectedPeers;
    }

    public HubPDUHubStatusRPLY(InputStream is) throws IOException, ASAPException {
        super(HUB_STATUS_REPLY);
        this.connectedPeers = ASAPSerialization.readCharSequenceSetParameter(is);
    }

    @Override
    public void sendPDU(OutputStream os) throws IOException {
        super.sendPDUNumber(os);
        ASAPSerialization.writeCharSequenceSetParameter(this.connectedPeers, os);
    }
}
