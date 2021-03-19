package net.sharksystem.hub;

import net.sharksystem.asap.ASAPException;
import net.sharksystem.asap.utils.ASAPSerialization;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.util.Collection;

import static net.sharksystem.hub.TestConstants.*;

public class HubUsageTests {
    String ROOT_FOLDER = TestConstants.ROOT_DIRECTORY + HubUsageTests.class.getSimpleName() + "/";
    int maxTimeInSeconds = 1;

    @Test
    public void usage() throws IOException, InterruptedException, ASAPException {
        int specificPort = 6907;
        CharSequence host = "localhost";
        TCPHubEntity hub = new TCPHubEntity(specificPort);
        hub.setPortRange(7000, 9000); // optional - required to configure a firewall
        hub.setMaxIdleConnectionInSeconds(maxTimeInSeconds);
        hub.start();

        HubConnector aliceHubConnector = TCPHubConnector.createTCPHubConnector(host, specificPort);
        HubConnectorTester aliceListener = new HubConnectorTester(ALICE_ID);
        aliceHubConnector.setListener(aliceListener);

        aliceHubConnector.connectHub(ALICE_ID);
        Thread.sleep(100);
        Collection<CharSequence> peerNames = aliceHubConnector.getPeerIDs();
        Assert.assertEquals(0, peerNames.size());

        HubConnectorTester bobListener = new HubConnectorTester(BOB_ID);
        HubConnector bobHubConnector = TCPHubConnector.createTCPHubConnector(host, specificPort);
        bobHubConnector.setListener(bobListener);
        bobHubConnector.connectHub(BOB_ID);
        Thread.sleep(100);

        peerNames = bobHubConnector.getPeerIDs();
        Assert.assertEquals(1, peerNames.size());

        aliceHubConnector.syncHubInformation();
        Thread.sleep(100);
        peerNames = aliceHubConnector.getPeerIDs();
        Assert.assertEquals(1, peerNames.size());

        bobHubConnector.syncHubInformation();

        /// Alice meets Bob
        aliceHubConnector.connectPeer(BOB_ID);

        Thread.sleep(maxTimeInSeconds * 1000 * 3);
        System.out.println("************************ back from sleep ****************************");
        //Thread.sleep(Long.MAX_VALUE);
        Assert.assertEquals(1, aliceListener.numberNotifications());
        Assert.assertEquals(1, bobListener.numberNotifications());

        System.out.println("************************ alice connector disconnect ****************************");
        aliceHubConnector.disconnectHub();
        Thread.sleep(maxTimeInSeconds * 1000 * 5);

        System.out.println("************************ bob connector sync ****************************");
        bobHubConnector.syncHubInformation();
        Thread.sleep(100);

        peerNames = bobHubConnector.getPeerIDs();
        Assert.assertEquals(0, peerNames.size());
    }

    class HubConnectorTester implements NewConnectionListener {
        private final String peerID;
        private int numberNofications = 0;

        public HubConnectorTester(String peerID) {
            this.peerID = peerID;
        }

        @Override
        public void notifyPeerConnected(SessionConnection sessionConnection) {
            System.out.println("listener of " + peerID + " got notified about a connection ");
            this.numberNofications++;

            try {
                //Thread.sleep(1500);
                String message = this.peerID;
                ASAPSerialization.writeCharSequenceParameter(message, sessionConnection.getOutputStream());
                // read
                String receivedMessage = ASAPSerialization.readCharSequenceParameter(sessionConnection.getInputStream());
                System.out.println(this.peerID + " received: " + receivedMessage);

                /*
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                 */
                message = "Hi: " + receivedMessage;
                //System.out.println(this.peerID + " going to send " + message);
                ASAPSerialization.writeCharSequenceParameter(message, sessionConnection.getOutputStream());
                //System.out.println(this.peerID + " going to read ");
                receivedMessage = ASAPSerialization.readCharSequenceParameter(sessionConnection.getInputStream());
                // read
                System.out.println(this.peerID + " received#2: " + receivedMessage);
                sessionConnection.close();
            } catch (ASAPException e) {
                System.out.println(this.peerID + " asapException ");
                e.printStackTrace();
            } catch (IOException iex) {
                System.out.println(this.peerID + " ioException ");
                iex.printStackTrace();
            }
        }

        public int numberNotifications() {
            return this.numberNofications;
        }
    }
}
