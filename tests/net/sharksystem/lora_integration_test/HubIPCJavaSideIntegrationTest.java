package net.sharksystem.lora_integration_test;

import net.sharksystem.hub.ASAPHubException;
import net.sharksystem.hub.hubside.ConnectorInternal;
import net.sharksystem.hub.hubside.HubIPCJavaSide;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;
import org.mockito.stubbing.Answer;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.*;

// make sure that tests were executed in correct order
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class HubIPCJavaSideIntegrationTest {

    private final String hostA = "localhost";
    private final String hostB = "localhost";
    private final int ipcPortA = 6000;
    private final int messagePortA = 6100;
    private final int ipcPortB = 6200;
    private final int messagePortB = 6300;



    @Test
    public void stage01RegisterUnregisterPeers() throws IOException, InterruptedException {
        HubIPCJavaSide hubIPCJavaSideA = new HubIPCJavaSide(hostA, ipcPortA, messagePortA);
        hubIPCJavaSideA.startReadingThread();

        HubIPCJavaSide hubIPCJavaSideB = new HubIPCJavaSide(hostB, ipcPortB, messagePortB);
        hubIPCJavaSideB.startReadingThread();

        ConnectorInternal connectorInternalA = new ConnectorInternalLocalStub(null, null);
        ConnectorInternal connectorInternalB = new ConnectorInternalLocalStub(null, null);

        // register Alice on first IPC instance
        hubIPCJavaSideA.register("Alice", connectorInternalA);
        // register Bob on second IPC instance
        hubIPCJavaSideB.register("Bob", connectorInternalB);

        // check whether peerIds are registered on both instances
        assertTrue(this.checkPeerRegistered(hubIPCJavaSideA, "Alice"));
        assertTrue(this.checkPeerRegistered(hubIPCJavaSideB, "Alice"));
        assertTrue(this.checkPeerRegistered(hubIPCJavaSideB, "Bob"));
        assertTrue(this.checkPeerRegistered(hubIPCJavaSideA, "Bob"));

        // unregister Alice and Bob
        hubIPCJavaSideA.unregister("Alice");
        hubIPCJavaSideB.unregister("Bob");

        // check whether peerIds were unregistered from both instances
        assertTrue(this.checkPeerIsNotRegistered(hubIPCJavaSideA, "Alice"));
        assertTrue(this.checkPeerIsNotRegistered(hubIPCJavaSideB, "Alice"));
        assertTrue(this.checkPeerIsNotRegistered(hubIPCJavaSideB, "Bob"));
        assertTrue(this.checkPeerIsNotRegistered(hubIPCJavaSideA, "Bob"));

        // close connection to IPC instance
        hubIPCJavaSideA.closeIPCConnection();
        hubIPCJavaSideB.closeIPCConnection();
        Thread.sleep(2000);
    }

    @Test(timeout = 120000) // set timeout to two minutes
    public void stage02RegisterPeersAndSendMessage() throws IOException, InterruptedException, ASAPHubException {
        HubIPCJavaSide hubIPCJavaSideA = new HubIPCJavaSide(hostA, ipcPortA, messagePortA);
        HubIPCJavaSide hubIPCJavaSideB = new HubIPCJavaSide(hostB, ipcPortB, messagePortB);

        InputStream inputStreamA = new ByteArrayInputStream("hello".getBytes(StandardCharsets.UTF_8));
//        InputStream inputStreamA = mock(InputStream.class);
        // send 'hello'
//        when(inputStreamA.read()).thenReturn(104).thenReturn(101).thenReturn(108).thenReturn(108).thenReturn(111);
//                .thenAnswer((Answer) invocation -> {
//                    try {
//                        // make sure stream keeps open before test case ends
//                        Thread.sleep(90000);
//                        return null;
//                    } catch (InterruptedException ie) {
//                        throw new RuntimeException(ie);
//                    }
//
//                }).thenReturn(-1); // after 45 seconds the stream return -1, because there are not any data anymore
        OutputStream outputStreamA = new ByteArrayOutputStream();
        ConnectorInternal connectorInternalA = new ConnectorInternalLocalStub(inputStreamA, outputStreamA);

        hubIPCJavaSideA.startReadingThread();
        hubIPCJavaSideB.startReadingThread();

        // register peer with peer id 'Alice'
        hubIPCJavaSideA.register("Alice", connectorInternalA);
        assertTrue(this.checkPeerRegistered(hubIPCJavaSideA, "Alice"));

        // make sure stream keeps open before test case ends
        InputStream inputStreamB = mock(InputStream.class);
        when(inputStreamB.read()).thenAnswer((Answer) invocation -> {
            try {
                Thread.sleep(90000);
                return null;
            } catch (InterruptedException ie) {
                throw new RuntimeException(ie);
            }

        }).thenReturn(-1).thenReturn(-1);
        OutputStream outputStreamB = new ByteArrayOutputStream();
        ConnectorInternal connectorInternalB = new ConnectorInternalLocalStub(inputStreamB, outputStreamB);

        // register peer with peer id 'Bob'
        hubIPCJavaSideB.register("Bob", connectorInternalB);
        assertTrue(this.checkPeerRegistered(hubIPCJavaSideB, "Bob"));

        // Alice sends a connectionRequest to Bob
        hubIPCJavaSideA.connectionRequest("Alice", "Bob", 60);

        // wait until whole message was received
        while (true) {
            if (outputStreamB.toString().length() > 4) {
                break;
            }
        }
        // verify message from Alice was received by Bob
        assertEquals("hello", outputStreamB.toString());
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
}