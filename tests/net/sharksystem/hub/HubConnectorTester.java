package net.sharksystem.hub;

import net.sharksystem.streams.StreamPair;
import net.sharksystem.asap.ASAPException;
import net.sharksystem.asap.utils.ASAPSerialization;
import net.sharksystem.hub.peerside.NewConnectionListener;

import java.io.IOException;

class HubConnectorTester implements NewConnectionListener {
    private final String peerID;
    private int numberNofications = 0;

    public HubConnectorTester(String peerID) {
        this.peerID = peerID;
    }

    @Override
    public void notifyPeerConnected(CharSequence targetPeerID, StreamPair streamPair) {
        System.out.println("listener of " + peerID + " got notified about a connection ");
        this.numberNofications++;

        try {
            //Thread.sleep(1500);
            System.out.println(peerID + " writes into stream...");
            String message = this.peerID;
            ASAPSerialization.writeCharSequenceParameter(message, streamPair.getOutputStream());
            // read
            System.out.println(peerID + " start reading from stream...");
            String receivedMessage = ASAPSerialization.readCharSequenceParameter(streamPair.getInputStream());
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
            ASAPSerialization.writeCharSequenceParameter(message, streamPair.getOutputStream());
            //System.out.println(this.peerID + " going to read ");
            receivedMessage = ASAPSerialization.readCharSequenceParameter(streamPair.getInputStream());
            // read
            System.out.println(this.peerID + " received#2: " + receivedMessage);
            streamPair.close();
        } catch (ASAPException e) {
            System.out.println(this.peerID + " asapException ");
            //e.printStackTrace();
        } catch (IOException iex) {
            System.out.println(this.peerID + " ioException ");
            //iex.printStackTrace();
        }
    }

    public int numberNotifications() {
        return this.numberNofications;
    }
}
