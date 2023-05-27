package net.sharksystem.hub;

import net.sharksystem.asap.ASAPException;
import net.sharksystem.asap.utils.ASAPSerialization;
import net.sharksystem.hub.peerside.HubConnectorDescription;
import net.sharksystem.hub.peerside.TCPHubConnectorDescriptionImpl;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class FailedConnectionAttemptImpl implements HubConnectionManager.FailedConnectionAttempt {

    private final HubConnectorDescription hubConnectorDescription;
    private final long timestamp;

    public FailedConnectionAttemptImpl(HubConnectorDescription hubConnectorDescription, long timestamp){
        this.hubConnectorDescription = hubConnectorDescription;
        this.timestamp = timestamp;
    }

    @Override
    public HubConnectorDescription getHubConnectorDescription() {
        return hubConnectorDescription;
    }

    @Override
    public long getTimeStamp() {
        return timestamp;
    }

    public static byte[] serializeFailedConnectionAttempts(List<HubConnectionManager.FailedConnectionAttempt> failedConnectionAttempts) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        if(failedConnectionAttempts == null || failedConnectionAttempts.isEmpty()) {
            // no hcd
            ASAPSerialization.writeIntegerParameter(0, baos);
        }else {
            // write number of descriptions
            ASAPSerialization.writeIntegerParameter(failedConnectionAttempts.size(), baos);
            for (HubConnectionManager.FailedConnectionAttempt failedConnectionAttempt : failedConnectionAttempts) {
                // HubConnectorDescription
                baos.write(failedConnectionAttempt.getHubConnectorDescription().serialize());
                // timestamp
                ASAPSerialization.writeLongParameter(failedConnectionAttempt.getTimeStamp(), baos);
            }
        }
        return baos.toByteArray();
    }

    public static List<HubConnectionManager.FailedConnectionAttempt> deserializeFailedConnectionAttempts(byte[] serializedConnectionAttempts) throws IOException, ASAPException {
        List<HubConnectionManager.FailedConnectionAttempt> connectionAttempts = new ArrayList<>();
        ByteArrayInputStream is = new ByteArrayInputStream(serializedConnectionAttempts);
        int number = ASAPSerialization.readIntegerParameter(is);
        while(number-- > 0) {
            HubConnectorDescription hcd = TCPHubConnectorDescriptionImpl.readDescription(is);
            long timestamp = ASAPSerialization.readLongParameter(is);
            connectionAttempts.add(new FailedConnectionAttemptImpl(hcd, timestamp));
        }
        return connectionAttempts;
    }
}
