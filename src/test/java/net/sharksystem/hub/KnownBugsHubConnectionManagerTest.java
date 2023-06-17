package net.sharksystem.hub;

import net.sharksystem.SharkException;
import net.sharksystem.asap.ASAPEncounterManagerImpl;
import net.sharksystem.asap.ASAPPeerFS;
import net.sharksystem.asap.apps.testsupport.ASAPTestPeerFS;
import net.sharksystem.hub.hubside.ASAPTCPHub;
import net.sharksystem.hub.peerside.HubConnectorDescription;
import net.sharksystem.hub.peerside.TCPHubConnectorDescriptionImpl;
import net.sharksystem.utils.testsupport.TestHelper;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.Collections;

import static net.sharksystem.hub.TestConstants.*;
import static org.junit.Assert.assertEquals;

public class KnownBugsHubConnectionManagerTest {
    private HubConnectionManager hubConnectionManager;
    private ASAPTCPHub asapHub;
    private int hubPort;
    private final boolean multiChannel = false;

    @Before
    public void setUp() throws Exception {
        hubPort = TestHelper.getPortNumber();
        ASAPPeerFS asapPeer = new ASAPTestPeerFS(ALICE_ID, Collections.singletonList(FORMAT));
        hubConnectionManager = new HubConnectionManagerImpl(new ASAPEncounterManagerImpl(asapPeer), asapPeer);
        asapHub = ASAPTCPHub.startTCPHubThread(hubPort, multiChannel, MAX_IDLE_IN_SECONDS);
    }

    @After
    public void teardown() {
        asapHub.kill();
    }

    @Test
    public void connectHubEdgeTwoAttempts() throws IOException, SharkException, InterruptedException {
        HubConnectorDescription hubDescriptionSecondHub = new TCPHubConnectorDescriptionImpl("localhost",
                hubPort + 1, multiChannel);
        hubConnectionManager.connectHub(hubDescriptionSecondHub);
        // give it some time for connection attempt
        Thread.sleep(1000);

        // first attempt should fail, because hub wasn't started yet
        assertEquals(0, hubConnectionManager.getConnectedHubs().size());
        assertEquals(1, hubConnectionManager.getFailedConnectionAttempts().size());

        ASAPTCPHub asapHub2 = ASAPTCPHub.startTCPHubThread(hubPort+1, multiChannel, MAX_IDLE_IN_SECONDS);
        // give it some time for reconnecting after hub was started
        Thread.sleep(2000);
        // connection should be established now
        assertEquals(1, hubConnectionManager.getConnectedHubs().size());
        assertEquals(2, hubConnectionManager.getFailedConnectionAttempts().size());

        asapHub2.kill();
    }

}