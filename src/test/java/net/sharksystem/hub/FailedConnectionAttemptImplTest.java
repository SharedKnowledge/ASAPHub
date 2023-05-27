package net.sharksystem.hub;

import net.sharksystem.asap.ASAPException;
import net.sharksystem.hub.peerside.HubConnectorDescription;
import net.sharksystem.hub.peerside.TCPHubConnectorDescriptionImpl;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

public class FailedConnectionAttemptImplTest {

    @Test
    public void serializeFailedConnectionAttempts() throws IOException, ASAPException {
        HubConnectorDescription hubConnectorDescription1 = new TCPHubConnectorDescriptionImpl("localhost", 6000);
        HubConnectorDescription hubConnectorDescription2 = new TCPHubConnectorDescriptionImpl("localhost", 6600);
        long timestamp1 = 10;
        long timestamp2 = 10;
        List<HubConnectionManager.FailedConnectionAttempt> failedConnectionAttempts = new ArrayList<>();
        failedConnectionAttempts.add(new FailedConnectionAttemptImpl(hubConnectorDescription1, timestamp1));
        failedConnectionAttempts.add(new FailedConnectionAttemptImpl(hubConnectorDescription2, timestamp2));

        byte[] serializedAttempts = FailedConnectionAttemptImpl.serializeFailedConnectionAttempts(failedConnectionAttempts);

        List<HubConnectionManager.FailedConnectionAttempt> deserializedAttempts = FailedConnectionAttemptImpl.deserializeFailedConnectionAttempts(serializedAttempts);

        // list should contain two items
        assertEquals(2, deserializedAttempts.size());
        // verify first FailedConnectionAttempt
        assertTrue(deserializedAttempts.get(0).getHubConnectorDescription().isSame(hubConnectorDescription1));
        assertEquals(timestamp1, deserializedAttempts.get(0).getTimeStamp());
        // verify second FailedConnectionAttempt
        assertTrue(deserializedAttempts.get(1).getHubConnectorDescription().isSame(hubConnectorDescription2));
        assertEquals(timestamp2, deserializedAttempts.get(1).getTimeStamp());
    }
}