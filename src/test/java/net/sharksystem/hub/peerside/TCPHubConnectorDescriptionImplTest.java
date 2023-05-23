package net.sharksystem.hub.peerside;

import net.sharksystem.asap.ASAPException;
import net.sharksystem.hub.HubConnectionManagerImpl;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

public class TCPHubConnectorDescriptionImplTest {
    private final String HOST_NAME_SERVER_A = "10.20.35.100";
    private final String HOST_NAME_SERVER_B = "10.20.35.200";
    private final int PORT_SERVER_A = 6910;
    private final int PORT_SERVER_B = 6000;

    private HubConnectorDescription hubConnectorDescriptionServerA;
    private HubConnectorDescription hubConnectorDescriptionServerB;

    @Before
    public void setup() throws IOException {
        this.hubConnectorDescriptionServerA = new TCPHubConnectorDescriptionImpl(HOST_NAME_SERVER_A, PORT_SERVER_A,
                true);
        this.hubConnectorDescriptionServerB = new TCPHubConnectorDescriptionImpl(HOST_NAME_SERVER_B, PORT_SERVER_B,
                false);
    }

    @Test
    public void serializeAndDeserializeHubConnectorDescriptionListGood() throws IOException, ASAPException {
        byte[] serialized = TCPHubConnectorDescriptionImpl.serializeConnectorDescriptionList(Arrays.asList(hubConnectorDescriptionServerA, hubConnectorDescriptionServerB));
        List<HubConnectorDescription> hcdList = TCPHubConnectorDescriptionImpl.deserializeHubConnectorDescriptionList(serialized);

        assertEquals(2, hcdList.size());

        // verify content of server_a
        assertEquals(HOST_NAME_SERVER_A, hcdList.get(0).getHostName());
        assertEquals(PORT_SERVER_A, hcdList.get(0).getPortNumber());
        // verify content of server_b
        assertEquals(HOST_NAME_SERVER_B, hcdList.get(1).getHostName());
        assertEquals(PORT_SERVER_B, hcdList.get(1).getPortNumber());
    }

    @Test
    public void serializeAndDeserializeHubConnectorDescriptionListEdgeEmpty() throws IOException, ASAPException {
        byte[] serialized = TCPHubConnectorDescriptionImpl.serializeConnectorDescriptionList(new ArrayList<>());
        List<HubConnectorDescription> hcdList = TCPHubConnectorDescriptionImpl.deserializeHubConnectorDescriptionList(serialized);
        assertEquals(0, hcdList.size());
    }
}