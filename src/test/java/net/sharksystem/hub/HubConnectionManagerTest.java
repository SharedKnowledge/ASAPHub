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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

import static net.sharksystem.hub.TestConstants.*;
import static org.junit.Assert.*;

public class HubConnectionManagerTest {
    private HubConnectionManager hubConnectionManager;
    private ASAPPeerFS asapPeer;
    private ASAPTCPHub asapHub;
    private HubConnectorDescription localHostHubDescription;
    private int hubPort;
    private boolean multiChannel = false;

    @Before
    public void setUp() throws Exception {
        hubPort = TestHelper.getPortNumber();
        localHostHubDescription = new TCPHubConnectorDescriptionImpl("localhost", hubPort, multiChannel);
        asapPeer = new ASAPTestPeerFS(ALICE_ID, Collections.singletonList(FORMAT));
        hubConnectionManager = new HubConnectionManagerImpl(new ASAPEncounterManagerImpl(asapPeer), asapPeer);
    }

    @After
    public void teardown() {
        asapHub.kill();
    }

    @Test
    public void connectHubGood() throws IOException, SharkException, InterruptedException {
        asapHub = ASAPTCPHub.startTCPHubThread(hubPort, multiChannel, MAX_IDLE_IN_SECONDS);
        hubConnectionManager.connectHub(localHostHubDescription);
        // give it some time for connection attempt
        Thread.sleep(1000);

        // connected hub list should contain one item
        assertEquals(1, hubConnectionManager.getConnectedHubs().size());
        // there shouldn't be failed connection attempts
        assertEquals(0, hubConnectionManager.getFailedConnectionAttempts().size());
    }

    @Test
    public void connectHubGoodMultipleConnections() throws IOException, SharkException, InterruptedException {
        asapHub = ASAPTCPHub.startTCPHubThread(hubPort, multiChannel, MAX_IDLE_IN_SECONDS);
        ASAPTCPHub asapHub2 = ASAPTCPHub.startTCPHubThread(hubPort+1, multiChannel, MAX_IDLE_IN_SECONDS);
        HubConnectorDescription hubDescriptionSecondHub = new TCPHubConnectorDescriptionImpl("localhost",
                hubPort + 1, multiChannel);
        hubConnectionManager.connectHub(localHostHubDescription);
        hubConnectionManager.connectHub(hubDescriptionSecondHub);
        // give it some time for connection attempt
        Thread.sleep(1000);

        // connected hub list should contain two items
        assertEquals(2, hubConnectionManager.getConnectedHubs().size());
        // there shouldn't be failed connection attempts
        assertEquals(0, hubConnectionManager.getFailedConnectionAttempts().size());

        asapHub2.kill();
    }

    @Test
    public void connectHubBad() throws IOException, SharkException, InterruptedException {
        asapHub = ASAPTCPHub.startTCPHubThread(hubPort, multiChannel, MAX_IDLE_IN_SECONDS);

        HubConnectorDescription hubDescriptionWrongPort = new TCPHubConnectorDescriptionImpl("localhost",
                hubPort + 1, multiChannel);
        hubConnectionManager.connectHub(hubDescriptionWrongPort);
        // give it some time for connection attempt
        Thread.sleep(1000);

        // connected hub list should be empty
        assertEquals(0, hubConnectionManager.getConnectedHubs().size());
        // there should be one failed connection attempt
        assertEquals(1, hubConnectionManager.getFailedConnectionAttempts().size());
    }

    @Test
    public void connectHubBadTwoAttempts() throws IOException, SharkException, InterruptedException {
        asapHub = ASAPTCPHub.startTCPHubThread(hubPort, multiChannel, MAX_IDLE_IN_SECONDS);

        HubConnectorDescription hubDescription1 = new TCPHubConnectorDescriptionImpl("localhost",
                hubPort + 1, multiChannel);
        HubConnectorDescription hubDescription2 = new TCPHubConnectorDescriptionImpl("localhost",
                hubPort + 2, multiChannel);
        hubConnectionManager.connectHub(hubDescription1);
        // give it some time for connection attempt
        Thread.sleep(1000);
        hubConnectionManager.connectHub(hubDescription2);
        Thread.sleep(1000);

        // connected hub list should be empty
        assertEquals(0, hubConnectionManager.getConnectedHubs().size());
        // there should be two failed connection attempts, because two different hubs were used
        assertEquals(2, hubConnectionManager.getFailedConnectionAttempts().size());
    }

    @Test
    public void connectHubBadMultipleAttempts() throws IOException, SharkException, InterruptedException {
        asapHub = ASAPTCPHub.startTCPHubThread(hubPort, multiChannel, MAX_IDLE_IN_SECONDS);

        HubConnectorDescription hubDescriptionWrongPort = new TCPHubConnectorDescriptionImpl("localhost",
                hubPort + 1, multiChannel);
        hubConnectionManager.connectHub(hubDescriptionWrongPort);
        // give it some time for connection attempt
        Thread.sleep(1000);
        hubConnectionManager.connectHub(hubDescriptionWrongPort);
        Thread.sleep(1000);

        // connected hub list should be empty
        assertEquals(0, hubConnectionManager.getConnectedHubs().size());
        // there should be only one failed connection attempt, because the same hubConnectorDescription object was used
        // for the second attempt
        assertEquals(1, hubConnectionManager.getFailedConnectionAttempts().size());
    }
}