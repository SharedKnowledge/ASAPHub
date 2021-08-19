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
    private int numberNotifications = 0;
    public boolean error = false;

    public HubConnectorTester(String peerID) {
        this.peerID = peerID;
    }

    @Override
    public void notifyPeerConnected(CharSequence targetPeerID, StreamPair streamPair) {
        System.out.println("listener of " + peerID + " got notified about a connection ");
        this.numberNotifications++;
        this.error = false;

        try {
            //Thread.sleep(1500);
            //String message = this.peerID;
            String message = "AAAAAAAAAAA";
            System.out.println(peerID + " writes into stream: " + message);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ASAPSerialization.writeCharSequenceParameter(message, baos);
            byte[] outBytes = baos.toByteArray();
            ASAPSerialization.writeByteArray(outBytes, streamPair.getOutputStream());

            // read
            System.out.println(peerID + " reading..");
            if(PeerIDHelper.sameID(peerID, ALICE_ID)) {
                int i = 42; // debug break
            }
            byte[] inBytes = ASAPSerialization.readByteArray(streamPair.getInputStream());
            ByteArrayInputStream bais = new ByteArrayInputStream(inBytes);
            String receivedMessage = ASAPSerialization.readCharSequenceParameter(bais);

            System.out.println(this.peerID + " received: " + receivedMessage);

            // test
            if(!Helper.sameByteArray(outBytes, inBytes)) this.error = true;

            /*
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
             */
//            message = "Hi: " + receivedMessage;
            message = "YY";
            System.out.println(this.peerID + " going to send " + message);
            baos = new ByteArrayOutputStream();
            ASAPSerialization.writeCharSequenceParameter(message, baos);
            outBytes = baos.toByteArray();
            ASAPSerialization.writeByteArray(outBytes, streamPair.getOutputStream());

            System.out.println(this.peerID + " reading.. ");
            inBytes = ASAPSerialization.readByteArray(streamPair.getInputStream());
            bais = new ByteArrayInputStream(inBytes);
            receivedMessage = ASAPSerialization.readCharSequenceParameter(bais);

            // read
            System.out.println(this.peerID + " received#2: " + receivedMessage);

            // test
            if(!Helper.sameByteArray(outBytes, inBytes)) this.error = true;

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
