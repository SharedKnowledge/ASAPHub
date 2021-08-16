package net.sharksystem.lora_integration_test;

import net.sharksystem.hub.ASAPHubException;
import net.sharksystem.hub.hubside.ConnectorInternal;
import net.sharksystem.hub.hubside.HubIPCJavaSide;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

// make sure that tests were executed in correct order
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class HubIPCJavaSideIntegrationTest {

    private final String hostA = "localhost";
    private final String hostB = "localhost";
    private final int ipcPortA = 6000;
    private final int messagePortA = 6100;
    private final int ipcPortB = 6200;
    private final int messagePortB = 6300;
    private final String peerIdA = "Alice";
    private final String peerIdB = "Bob";


    @Test
    public void stage01RegisterUnregisterPeers() throws IOException, InterruptedException {
        // create two HubIPCJavaSide instances to simulate a distributed hub on peer Alice and peer Bob
        HubIPCJavaSide hubIPCJavaSideA = new HubIPCJavaSide(hostA, ipcPortA, messagePortA);
        hubIPCJavaSideA.startReadingThread();
        HubIPCJavaSide hubIPCJavaSideB = new HubIPCJavaSide(hostB, ipcPortB, messagePortB);
        hubIPCJavaSideB.startReadingThread();

        ConnectorInternal connectorInternalA = new ConnectorInternalLocalStub(null, null);
        ConnectorInternal connectorInternalB = new ConnectorInternalLocalStub(null, null);

        // register Alice on first IPC instance
        hubIPCJavaSideA.register(this.peerIdA, connectorInternalA);
        // register Bob on second IPC instance
        hubIPCJavaSideB.register(this.peerIdB, connectorInternalB);

        // check whether peerIds are registered on both instances
        assertTrue(this.checkPeerRegistered(hubIPCJavaSideA, this.peerIdA));
        assertTrue(this.checkPeerRegistered(hubIPCJavaSideB, this.peerIdA));
        assertTrue(this.checkPeerRegistered(hubIPCJavaSideB, this.peerIdB));
        assertTrue(this.checkPeerRegistered(hubIPCJavaSideA, this.peerIdB));

        // unregister Alice and Bob
        hubIPCJavaSideA.unregister("Alice");
        hubIPCJavaSideB.unregister("Bob");

        // check whether peerIds were unregistered from both instances
        assertTrue(this.checkPeerIsNotRegistered(hubIPCJavaSideA, this.peerIdA));
        assertTrue(this.checkPeerIsNotRegistered(hubIPCJavaSideB, this.peerIdA));
        assertTrue(this.checkPeerIsNotRegistered(hubIPCJavaSideB, this.peerIdB));
        assertTrue(this.checkPeerIsNotRegistered(hubIPCJavaSideA, this.peerIdB));

        // close connection to IPC instance
        hubIPCJavaSideA.closeIPCConnection();
        hubIPCJavaSideB.closeIPCConnection();
        Thread.sleep(2000);
    }

    @Test(timeout = 120000) // set timeout to two minutes
    public void stage02RegisterPeersAndSendMessage() throws IOException, InterruptedException, ASAPHubException {
        HubIPCJavaSide hubIPCJavaSideA = new HubIPCJavaSide(hostA, ipcPortA, messagePortA);
        HubIPCJavaSide hubIPCJavaSideB = new HubIPCJavaSide(hostB, ipcPortB, messagePortB);
        hubIPCJavaSideA.startReadingThread();
        hubIPCJavaSideB.startReadingThread();

        // Alice sends a message to Bob
        InputStream inputStreamA = new StringInputStream("hello bob");
        // ByteArrayOutput to capture Alice's received messages
        OutputStream outputStreamA = new ByteArrayOutputStream();
        ConnectorInternal connectorInternalA = new ConnectorInternalLocalStub(inputStreamA, outputStreamA);


        // register peer with peer id 'Alice'
        hubIPCJavaSideA.register("Alice", connectorInternalA);
        assertTrue(this.checkPeerRegistered(hubIPCJavaSideA, this.peerIdA));

        // Bob sends a message to Alice
        InputStream inputStreamB = new StringInputStream("hello alice");
        // ByteArrayOutput to capture Bob's received messages
        OutputStream outputStreamB = new ByteArrayOutputStream();
        ConnectorInternal connectorInternalB = new ConnectorInternalLocalStub(inputStreamB, outputStreamB);

        // register peer with peer id 'Bob'
        hubIPCJavaSideB.register("Bob", connectorInternalB);
        assertTrue(this.checkPeerRegistered(hubIPCJavaSideB, this.peerIdB));
        assertTrue(this.checkPeerRegistered(hubIPCJavaSideA, this.peerIdB));

        // Alice sends a connectionRequest to Bob
        hubIPCJavaSideA.connectionRequest(this.peerIdA, this.peerIdB, 60);

        // wait until whole message was received
        while (true) {
            if (outputStreamB.toString().length() > 8) {
                break;
            }
        }
        // verify message from Alice was received by Bob
        assertEquals("hello bob", outputStreamB.toString());

        while (true) {
            if (outputStreamA.toString().length() > 10) {
                break;
            }
        }
        // verify message from Bob was received by Alice
        assertEquals("hello alice", outputStreamA.toString());
        assertTrue(hubIPCJavaSideB.hasActiveConnection());
        hubIPCJavaSideA.disconnect(this.peerIdA, this.peerIdB);
        int attempt = 0;
        boolean disconnected = false;
        while(attempt < 6){
            if(hubIPCJavaSideB.hasActiveConnection()){
                attempt++;
                Thread.sleep(1000);
            }else{
                disconnected = true;
                break;
            }
        }
        assertTrue(disconnected);
    }


    private boolean checkPeerRegistered(HubIPCJavaSide hubIPCJavaSide, CharSequence peerId) throws InterruptedException {
        for (int i = 0; i < 8; i++) {
            Set<CharSequence> list = hubIPCJavaSide.getRegisteredPeers();
            if (list.contains(peerId)) {
                return true;
            }
            Thread.sleep(1000);
        }
        return false;
    }

    /**
     * helper function for test purposes
     * @param hubIPCJavaSide
     * @param peerId
     * @return
     * @throws InterruptedException
     */
    private boolean checkPeerIsNotRegistered(HubIPCJavaSide hubIPCJavaSide, CharSequence peerId) throws InterruptedException {
        for (int i = 0; i < 8; i++) {
            Set<CharSequence> list = hubIPCJavaSide.getRegisteredPeers();
            if (!list.contains(peerId)) {
                return true;
            }
            Thread.sleep(1000);
        }
        return false;
    }

    /**
     * implementation of an InputStream, which accepts a String as parameter. Stream keeps open, after als
     * bytes of the String were read.
     */
    private class StringInputStream extends InputStream {

        private int readPosition = 0;
        private byte[] bytesToRead;

        public StringInputStream(String stringToRead){
            this.bytesToRead = stringToRead.getBytes(StandardCharsets.UTF_8);
        }

        @Override
        public int read() throws IOException {
            if(this.readPosition < this.bytesToRead.length){
                this.readPosition ++;
                return bytesToRead[this.readPosition-1];
            }
            try {
                Thread.sleep(100000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            return 0;
        }
    }
}