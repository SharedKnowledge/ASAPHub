package net.sharksystem.hub;

import net.sharksystem.SharkException;
import net.sharksystem.asap.ASAPEncounterManagerImpl;
import net.sharksystem.asap.ASAPException;
import net.sharksystem.asap.apps.testsupport.ASAPTestPeerFS;
import net.sharksystem.fs.FSUtils;
import net.sharksystem.hub.peerside.*;
import net.sharksystem.hub.hubside.ASAPTCPHub;
import net.sharksystem.utils.testsupport.TestHelper;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;

import static net.sharksystem.hub.TestConstants.*;

public class HubUsageTests {
    String ROOT_FOLDER = TestConstants.ROOT_DIRECTORY + HubUsageTests.class.getSimpleName() + "/";

    static int portNumber = 6907;
    static int getPort() {
        return portNumber++;
    }

    @Test
    public void usageSharedConnection() throws IOException, InterruptedException, ASAPException {
        this.runUsageTest(
                true,
                false,
                "NON_CAN_TCP",
                "YZ",
                false);
    }

    @Test
    public void usageNewConnection() throws IOException, InterruptedException, ASAPException {
        this.runUsageTest(
                true,
                true,
                "BOTH_CAN_TCP",
                "YZ",
                false);
    }

    @Test
    public void usageNewConnection2_0() throws IOException, InterruptedException, ASAPException {
        this.runUsageTest(
                true,
                false,
                "ALICE_CAN_TCP",
                "YZ",
                false);
    }

    @Test
    public void usageNewConnection2_1() throws IOException, InterruptedException, ASAPException {
        this.runUsageTest(
                true,
                false,
                "A",
                "Y",
                false);
    }

    @Test
    public void usageNewConnection2_2() throws IOException, InterruptedException, ASAPException {
        this.runUsageTest(
                true,
                false,
                "A",
                "XYZ",
                false);
    }

    @Test
    public void usageNewConnection3_0() throws IOException, InterruptedException, ASAPException {
        this.runUsageTest(
                false,
                true,
                "BOB_CAN_TCP",
                "YY",
                false);

        /*
        Die Länge der Nachrichten ist richtig. Da kommen zu viele Nullen an. Aber die Anzahl ist richtig.
        Ist das ein Problem des Lesens oder schreibens?
         */
    }

    @Test
    public void usageNewConnection3_1() throws IOException, InterruptedException, ASAPException {
        this.runUsageTest(
                false,
                true,
                "A",
                "Y",
                false);
    }

    public void runUsageTest(
            boolean aliceCanCreateTCPConnections,
            boolean bobCanCreateTCPConnections,
            String messageA, String messageB,
            boolean pureBytes)  throws IOException, InterruptedException, ASAPException {

        int maxTimeInSeconds = Connector.DEFAULT_TIMEOUT_IN_MILLIS / 1000;
        maxTimeInSeconds = maxTimeInSeconds > 0 ? maxTimeInSeconds : 1;
        int specificPort = getPort();
        CharSequence host = "localhost";
        ASAPTCPHub hub = new ASAPTCPHub(specificPort, true);
        hub.setPortRange(7000, 9000); // optional - required to configure a firewall
        hub.setMaxIdleConnectionInSeconds(maxTimeInSeconds);
        new Thread(hub).start();

        HubConnector aliceHubConnector = SharedTCPChannelConnectorPeerSide.createTCPHubConnector(host, specificPort);
        HubConnectorTester aliceListener = new HubConnectorTester(ALICE_ID, messageA, messageB, pureBytes);
        aliceHubConnector.addListener(aliceListener);

        aliceHubConnector.connectHub(ALICE_ID, aliceCanCreateTCPConnections);
        Thread.sleep(100);
        Collection<CharSequence> peerNames = aliceHubConnector.getPeerIDs();
        Assert.assertEquals(0, peerNames.size());

        HubConnectorTester bobListener = new HubConnectorTester(BOB_ID, messageA, messageB, pureBytes);
        HubConnector bobHubConnector = SharedTCPChannelConnectorPeerSide.createTCPHubConnector(host, specificPort);
        bobHubConnector.addListener(bobListener);
        bobHubConnector.connectHub(BOB_ID, bobCanCreateTCPConnections);
        Thread.sleep(100);

        peerNames = bobHubConnector.getPeerIDs();
        Assert.assertEquals(1, peerNames.size());

        aliceHubConnector.syncHubInformation();
        Thread.sleep(100);
        peerNames = aliceHubConnector.getPeerIDs();
        Assert.assertEquals(1, peerNames.size());

//        bobHubConnector.syncHubInformation();

        /// Alice meets Bob
        aliceHubConnector.connectPeer(BOB_ID);
        //Thread.sleep(Long.MAX_VALUE);

        Thread.sleep(maxTimeInSeconds * 1000);
        System.out.println("************************ back from sleep ****************************");
        //Thread.sleep(Long.MAX_VALUE);
        Assert.assertEquals(1, aliceListener.numberNotifications());
        Assert.assertEquals(1, bobListener.numberNotifications());
        Assert.assertFalse(aliceListener.error);
        Assert.assertFalse(bobListener.error);

        System.out.println("************************ alice connector disconnect ****************************");
        aliceHubConnector.disconnectHub();
        Thread.sleep(maxTimeInSeconds * 1000);
        Thread.sleep(maxTimeInSeconds * 1000);

        System.out.println("************************ bob connector sync ****************************");
        bobHubConnector.syncHubInformation();
        Thread.sleep(maxTimeInSeconds * 1000);

        peerNames = bobHubConnector.getPeerIDs();
        for(CharSequence name : peerNames) {
            System.out.println(name);
        }
        Assert.assertEquals(0, peerNames.size());
    }

    @Test
    public void usageHubManagerAndTwoPeers_MultiChannel_True() throws IOException, InterruptedException, SharkException {
        this.usageHubManagerAndTwoPeers(true);
    }

    @Test
    public void usageHubManagerAndTwoPeers_MultiChannel_False() throws IOException, InterruptedException, SharkException {
        this.usageHubManagerAndTwoPeers(false);
    }

    public void usageHubManagerAndTwoPeers(boolean multichannel) throws IOException, InterruptedException, SharkException {
        int hubPort = TestHelper.getPortNumber();
        HubConnectorDescription localHostHubDescription =
                new TCPHubConnectorDescriptionImpl("localhost", hubPort, multichannel);
        Collection<HubConnectorDescription> hubDescriptions = new ArrayList<>();
        hubDescriptions.add(localHostHubDescription);

        // launch asap hub
        ASAPTCPHub hub = ASAPTCPHub.startTCPHubThread(hubPort, multichannel, MAX_IDLE_IN_SECONDS);

        // give it moment to settle in
        Thread.sleep(1000);

        Collection<CharSequence> formats = new ArrayList<>();
        formats.add(FORMAT);

        //////////////////////////////// setup Alice
        FSUtils.removeFolder(ALICE_ROOTFOLDER);
        ASAPTestPeerFS aliceASAPPeer = new ASAPTestPeerFS(ALICE_ID, formats);

        // send a message
        aliceASAPPeer.sendASAPMessage(FORMAT, URI, "Hi there".getBytes());

        ////////////// setup Bob
        FSUtils.removeFolder(BOB_ROOTFOLDER);
        ASAPTestPeerFS bobASAPPeer = new ASAPTestPeerFS(BOB_ID, formats);
        DummyMessageReceivedListener bobListener = new DummyMessageReceivedListener(BOB_ID);
        bobASAPPeer.addASAPMessageReceivedListener(FORMAT, bobListener);

        ///////////////////// connect to hub - Alice
        // setup encounter manager with a connection handler
        ASAPEncounterManagerImpl aliceEncounterManager =
                new ASAPEncounterManagerImpl(aliceASAPPeer, aliceASAPPeer.getPeerID());

        // setup hub manager
//        ASAPHubManager aliceHubManager = ASAPHubManagerImpl.startASAPHubManager(aliceEncounterManager);
        ASAPHubManager aliceHubManager = ASAPHubManagerImpl.createASAPHubManager(aliceEncounterManager);

        // connect with bulk import
        aliceHubManager.connectASAPHubs(hubDescriptions, aliceASAPPeer, true);
        Thread.sleep(1000);

        ///////////////////// connect to hub - Bob
        // setup encounter manager with a connection handler
        ASAPEncounterManagerImpl bobEncounterManager =
                new ASAPEncounterManagerImpl(bobASAPPeer, bobASAPPeer.getPeerID());

        // setup hub manager
//        ASAPHubManager bobHubManager = ASAPHubManagerImpl.startASAPHubManager(bobEncounterManager);
        ASAPHubManager bobHubManager = ASAPHubManagerImpl.createASAPHubManager(bobEncounterManager);

        // connect to hub - Bob
        bobHubManager.connectASAPHubs(hubDescriptions, bobASAPPeer, true);
        Thread.sleep(1000);

        // give them moment to exchange data
        Thread.sleep(5000);
        //Thread.sleep(Long.MAX_VALUE);
        System.out.println("slept a moment");

        // Bob received a message?
        Assert.assertEquals(1, bobListener.counter);

        // shut down
        hub.kill();
        aliceHubManager.kill();
        bobHubManager.kill();
    }
}