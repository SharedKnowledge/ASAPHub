package net.sharksystem.hub;

import net.sharksystem.asap.ASAPException;
import net.sharksystem.hub.hubside.TCPHub;
import net.sharksystem.hub.peerside.HubConnector;
import net.sharksystem.hub.peerside.SharedTCPChannelConnectorPeerSide;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.util.Collection;

import static net.sharksystem.hub.TestConstants.ALICE_ID;
import static net.sharksystem.hub.TestConstants.BOB_ID;

public class KnownBugsHubUsageTests {
    String ROOT_FOLDER = TestConstants.ROOT_DIRECTORY + HubUsageTests.class.getSimpleName() + "/";

    static int portNumber = 6907;
    static int getPort() {
        return portNumber++;
    }

/*
    @Test
    public void usageSharedConnection() throws IOException, InterruptedException, ASAPException {
        this.runUsageTest(false, false);
    }

    @Test
    public void usageNewConnection() throws IOException, InterruptedException, ASAPException {
        this.runUsageTest(true, true);
    }
 */

    @Test
    public void usageNewConnection2() throws IOException, InterruptedException, ASAPException {
        this.runUsageTest(true, false);
    }

    @Test
    public void usageNewConnection3() throws IOException, InterruptedException, ASAPException {
        this.runUsageTest(false, true);
    }

    public void runUsageTest(boolean aliceCanCreateTCPConnections, boolean bobCanCreateTCPConnections)
            throws IOException, InterruptedException, ASAPException {
        int maxTimeInSeconds = Connector.DEFAULT_TIMEOUT_IN_MILLIS / 1000;
        maxTimeInSeconds = maxTimeInSeconds > 0 ? maxTimeInSeconds : 1;
        int specificPort = getPort();
        CharSequence host = "localhost";
        TCPHub hub = new TCPHub(specificPort, true);
        hub.setPortRange(7000, 9000); // optional - required to configure a firewall
        hub.setMaxIdleConnectionInSeconds(maxTimeInSeconds);
        new Thread(hub).start();

        HubConnector aliceHubConnector = SharedTCPChannelConnectorPeerSide.createTCPHubConnector(host, specificPort);
        HubConnectorTester aliceListener = new HubConnectorTester(ALICE_ID);
        aliceHubConnector.addListener(aliceListener);

        aliceHubConnector.connectHub(ALICE_ID, aliceCanCreateTCPConnections);
        Thread.sleep(100);
        Collection<CharSequence> peerNames = aliceHubConnector.getPeerIDs();
        Assert.assertEquals(0, peerNames.size());

        HubConnectorTester bobListener = new HubConnectorTester(BOB_ID);
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

}
