package net.sharksystem.hub.peerside;

import net.sharksystem.asap.ASAP;
import net.sharksystem.asap.ASAPException;
import net.sharksystem.asap.utils.ASAPSerialization;
import net.sharksystem.hub.ASAPHubException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

public class HubConnectorFactory {
    public static final byte TCP = 0x00;

    public static HubConnector createTCPHubConnector(CharSequence hostName, int port)
            throws IOException, ASAPHubException {

        return SharedTCPChannelConnectorPeerSide.createTCPHubConnector(hostName, port);
    }

    public static byte[] createTCPConnectorDescription(CharSequence hostName, int port) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        ASAPSerialization.writeByteParameter(TCP, baos);
        ASAPSerialization.writeCharSequenceParameter(hostName, baos);
        ASAPSerialization.writeIntegerParameter(port, baos);

        return baos.toByteArray();
    }

    public static HubConnector createHubConnectorByDescription(byte[] description)
            throws IOException, ASAPException {

        ByteArrayInputStream bais = new ByteArrayInputStream(description);
        byte type = ASAPSerialization.readByte(bais);

        switch(type) {
            case TCP:
                String hostName = ASAPSerialization.readCharSequenceParameter(bais);
                int port = ASAPSerialization.readIntegerParameter(bais);
                return createTCPHubConnector(hostName, port);

            default: throw new ASAPHubException("unknown hub connector protocol type: " + type);
        }
    }
}
