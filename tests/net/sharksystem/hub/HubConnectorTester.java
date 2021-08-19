package net.sharksystem.hub;

import net.sharksystem.SharkException;
import net.sharksystem.SharkNotSupportedException;
import net.sharksystem.asap.utils.Helper;
import net.sharksystem.asap.utils.PeerIDHelper;
import net.sharksystem.streams.StreamPair;
import net.sharksystem.asap.ASAPException;
import net.sharksystem.asap.utils.ASAPSerialization;
import net.sharksystem.hub.peerside.NewConnectionListener;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static net.sharksystem.hub.TestConstants.ALICE_ID;

class HubConnectorTester implements NewConnectionListener {
    private final String peerID;
    private final String messageA;
    private final String messageB;
    private final boolean pureBytes;
    private int numberNotifications = 0;
    public boolean error = false;

    public static final String DEFAULT_MESSAGE_A = "AAAAAAAAAAA";
    public static final String DEFAULT_MESSAGE_B = "YY";

    public HubConnectorTester(String peerID) {
        this(peerID, DEFAULT_MESSAGE_A, DEFAULT_MESSAGE_B, false);
    }

    public HubConnectorTester(String peerID, String messageA, String messageB, boolean pureBytes) {
        this.peerID = peerID;
        this.messageA = messageA;
        this.messageB = messageB;
        this.pureBytes = pureBytes;
    }

    private void onError(byte[] expected, byte[] actual) {
        this.error = true;
        String expectedBytesAsString = ASAPSerialization.printByteArrayToString(expected);
        String actualBytesAsString = ASAPSerialization.printByteArrayToString(actual);


        System.err.println("Error on side: " + this.peerID + "\n"
                + expectedBytesAsString + "(expected)\n"
                + actualBytesAsString + "(actual)");

        /*
         * This is funny because that reference to a movie "Lost on translation"..
         */
        throw new SharkNotSupportedException("lost in transition");
    }

    @Override
    public void notifyPeerConnected(CharSequence targetPeerID, StreamPair streamPair) {
        System.out.println("listener of " + peerID + " got notified about a connection ");
        this.numberNotifications++;
        this.error = false;

        try {
            //Thread.sleep(1500);
            //String message = this.peerID;

            if(this.pureBytes) {

                byte[] testBytes = new byte[10];
                for(int i = 0; i < 10; i++) {
                    byte b = (byte) i;
                    testBytes[i] = b;
                }
                System.out.println(peerID + " writes pure bytes: " + this.messageA);
                streamPair.getOutputStream().write(testBytes);
                byte[] receivedBytes = new byte[10];
                System.out.println(peerID + " available: " + streamPair.getInputStream().available());
                Thread.sleep(100); // give other process a moment
                System.out.println(peerID + " available #2: " + streamPair.getInputStream().available());
                streamPair.getInputStream().read(receivedBytes);
                System.out.println(peerID + " available #3: " + streamPair.getInputStream().available());
                if(!Helper.sameByteArray(testBytes, receivedBytes)) this.onError(testBytes, receivedBytes);
                return;
            }

            System.out.println(peerID + " writes into stream: " + this.messageA);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ASAPSerialization.writeCharSequenceParameter(this.messageA, baos);
            byte[] outBytes = baos.toByteArray();
            ASAPSerialization.writeByteArray(outBytes, streamPair.getOutputStream());

            // read
            System.out.println(peerID + " reading..");
            if(PeerIDHelper.sameID(peerID, ALICE_ID)) {
                int i = 42; // debug break
            }
            Thread.sleep(1000);
            byte[] inBytes = ASAPSerialization.readByteArray(streamPair.getInputStream());
            ByteArrayInputStream bais = new ByteArrayInputStream(inBytes);
            String receivedMessage = ASAPSerialization.readCharSequenceParameter(bais);

            System.out.println(this.peerID + " received: " + receivedMessage);

            // test
            if(!Helper.sameByteArray(outBytes, inBytes)) this.onError(outBytes, inBytes);

            /*
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
             */
//            message = "Hi: " + receivedMessage;
            System.out.println(this.peerID + " going to send " + this.messageB);
            baos = new ByteArrayOutputStream();
            ASAPSerialization.writeCharSequenceParameter(this.messageB, baos);
            outBytes = baos.toByteArray();
            ASAPSerialization.writeByteArray(outBytes, streamPair.getOutputStream());

            System.out.println(this.peerID + " reading.. ");
            inBytes = ASAPSerialization.readByteArray(streamPair.getInputStream());
            bais = new ByteArrayInputStream(inBytes);
            receivedMessage = ASAPSerialization.readCharSequenceParameter(bais);

            // read
            System.out.println(this.peerID + " received#2: " + receivedMessage);

            // test
            if(!Helper.sameByteArray(outBytes, inBytes)) this.onError(outBytes, inBytes);

            // wait a moment
            Thread.sleep(10000);
            System.out.println(this.peerID + " close");
            streamPair.close();
        } catch (ASAPException e) {
            System.out.println(this.peerID + " asapException ");
            //e.printStackTrace();
        } catch (IOException iex) {
            System.out.println(this.peerID + " ioException ");
            //iex.printStackTrace();
        } catch (Error e) {
            System.out.println(this.peerID + " caught an error: ");
            e.printStackTrace();
        } catch (InterruptedException e) {
            System.out.println(this.peerID + "interrupted");
        }
    }

    public int numberNotifications() {
        return this.numberNotifications;
    }
}
