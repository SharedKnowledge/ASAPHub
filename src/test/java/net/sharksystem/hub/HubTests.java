package net.sharksystem.hub;

import net.sharksystem.asap.ASAPException;
import net.sharksystem.hub.hubside.ASAPTCPHub;
import net.sharksystem.hub.peerside.HubConnector;
import net.sharksystem.hub.peerside.SharedTCPChannelConnectorPeerSide;
import org.junit.Test;

import java.io.IOException;

import static net.sharksystem.hub.TestConstants.ALICE_ID;

public class HubTests {

    @Test
    public void usage() throws IOException, InterruptedException, ASAPException {
        int specificPort = 6907;
        CharSequence host = "localhost";
        ASAPTCPHub hub = new ASAPTCPHub(specificPort);
        hub.setPortRange(7000, 9000); // optional - required to configure a firewall
        int maxTimeInSeconds = Connector.DEFAULT_TIMEOUT_IN_MILLIS / 1000;
        maxTimeInSeconds = maxTimeInSeconds > 0 ? maxTimeInSeconds : 1;
        hub.setMaxIdleConnectionInSeconds(maxTimeInSeconds);
        new Thread(hub).start();

        HubConnector aliceHubConnector = SharedTCPChannelConnectorPeerSide.createTCPHubConnector(host, specificPort);
        HubConnectorTester aliceListener = new HubConnectorTester(ALICE_ID);
        aliceHubConnector.addListener(aliceListener);

        aliceHubConnector.connectHub(ALICE_ID);
    }

    @Test
    public void localAccess() throws IOException, InterruptedException, ASAPException {
        this.doConnect(ASAPTCPHub.DEFAULT_PORT, "localhost");
    }

    @Test
    public void remoteAccess() throws IOException, InterruptedException, ASAPException {
        this.doConnect(ASAPTCPHub.DEFAULT_PORT, "asaphub.f4.htw-berlin.de");
    }

    private void doConnect(int specificPort, CharSequence host) throws ASAPException, IOException, InterruptedException {
        HubConnector aliceHubConnector = SharedTCPChannelConnectorPeerSide.createTCPHubConnector(host, specificPort);
        HubConnectorTester aliceListener = new HubConnectorTester(ALICE_ID);
        aliceHubConnector.addListener(aliceListener);

        aliceHubConnector.connectHub(ALICE_ID, false);

        Thread.sleep(Long.MAX_VALUE);
    }
}
