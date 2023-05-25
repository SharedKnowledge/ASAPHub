package net.sharksystem.hub;

import net.sharksystem.asap.ASAPEncounterManagerImpl;
import net.sharksystem.asap.ASAPException;
import net.sharksystem.asap.apps.testsupport.ASAPTestPeerFS;
import net.sharksystem.hub.hubside.ASAPTCPHub;
import net.sharksystem.hub.peerside.ASAPHubManagerImpl;
import net.sharksystem.hub.peerside.HubConnector;
import net.sharksystem.hub.peerside.SharedTCPChannelConnectorPeerSide;
import net.sharksystem.utils.testsupport.TestHelper;
import org.junit.Test;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

public class HubManagerTests {
    public static final String ALICE_ROOT_FOLDER = "hubManagerTests/Alice";
    public static final String ALICE = "Alice";
    public static final String BOB = "Bob";
    public static final String FORMAT = "asap/hubmanagertests";


    @Test
    public void test1() throws IOException, ASAPException, InterruptedException {
        // launch hub
        int specificPort = TestHelper.getPortNumber();
        CharSequence host = "localhost";
        ASAPTCPHub hub = new ASAPTCPHub(specificPort);

        int maxTimeOutMillis = Connector.DEFAULT_TIMEOUT_IN_MILLIS;

        hub.setMaxIdleConnectionInSeconds(maxTimeOutMillis);
        new Thread(hub).start();

        Set formats = new HashSet();
        formats.add(FORMAT);

        // create alice peer
        ASAPTestPeerFS alicePeer = new ASAPTestPeerFS(ALICE, formats);

        // connect to hub
        HubConnector aliceHubConnector = SharedTCPChannelConnectorPeerSide.createTCPHubConnector(host, specificPort);
        HubConnectorTester aliceListener = new HubConnectorTester(ALICE);
        aliceHubConnector.addListener(aliceListener);

        // create alice BOB
        ASAPTestPeerFS bobPeer = new ASAPTestPeerFS(ALICE, formats);

        // connect to hub
        HubConnector bobHubConnector = SharedTCPChannelConnectorPeerSide.createTCPHubConnector(host, specificPort);
        HubConnectorTester bobListener = new HubConnectorTester(BOB);
        bobHubConnector.addListener(bobListener);

        // connect to hub
        aliceHubConnector.connectHub(ALICE);
        bobHubConnector.connectHub(BOB);

        // give it some time
        Thread.sleep(maxTimeOutMillis*2);

        // add to hub manager
        ASAPEncounterManagerImpl asapEncounterManager = new ASAPEncounterManagerImpl(alicePeer);
        ASAPHubManagerImpl asapASAPHubManager = new ASAPHubManagerImpl(asapEncounterManager);
        asapASAPHubManager.setTimeOutInMillis(maxTimeOutMillis);
        asapASAPHubManager.addHub(aliceHubConnector);

        // start hub manager
        new Thread(asapASAPHubManager).start();

        Thread.sleep(maxTimeOutMillis*2);
        //Thread.sleep(Long.MAX_VALUE);
    }
}
