package net.sharksystem.hub;

import net.sharksystem.asap.ASAPException;
import net.sharksystem.asap.utils.ASAPSerialization;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;

public class StreamLinkTests {
    private static int portnumber = TestConstants.DEFAULT_PORT;
    long maxIdleMillis = 100;

    private static int getPortNumber() {
        return StreamLinkTests.portnumber++;
    }

    @Test
    public void borrowedConnectionTest() throws IOException, InterruptedException {
        int port = getPortNumber();

        SocketFactory socketFactory = new SocketFactory(new ServerSocket(port));
        Thread socketFactoryThread = new Thread(socketFactory);
        socketFactoryThread.start();

        // wait a moment to settle
        Thread.sleep(10);

        Socket socket = new Socket("localhost", port);

        InputStream sideA_IS = socket.getInputStream();
        OutputStream sideA_OS = socket.getOutputStream();

        socketFactoryThread.join(); // wait until connection established
        InputStream sideB_IS = socketFactory.getInputStream();
        OutputStream sideB_OS = socketFactory.getOutputStream();

        // simulate protocol a exchange
        System.out.println("+++++++++++++++++ simulate protocol ++++++++++++++++++");
        int rounds = 5;
        DataExchangeTester aliceDataSession = new DataExchangeTester(sideA_IS, sideA_OS, rounds, "Alice");
        DataExchangeTester bobDataSession = new DataExchangeTester(sideB_IS, sideB_OS, rounds, "Bob");

        Thread aliceDataSessionThread = new Thread(aliceDataSession);
        Thread bobDataSessionThread = new Thread(bobDataSession);

        aliceDataSessionThread.start();
        bobDataSessionThread.start();

        // both will block
        Thread.sleep(100);
        System.out.println("+++++++++++++++++ kick both out from protocol ++++++++++++++++++");
        sideA_OS.write(42);
        sideB_OS.write(42);

        System.out.println("+++++++++++++++++ re-run with borrowed connection ++++++++++++++++++");
        BorrowedConnection aliceBorrowedConnection = new BorrowedConnection(sideA_IS, sideA_OS, "Alice (BC)", maxIdleMillis);
        BorrowedConnection bobBorrowedConnection = new BorrowedConnection(sideB_IS, sideB_OS, "Bob (BC)", 2*maxIdleMillis);

        Thread aliceBorrowedConnectionThread = new Thread(aliceBorrowedConnection);
        Thread bobBorrowedConnectionThread = new Thread(bobBorrowedConnection);

        aliceBorrowedConnectionThread.start();
        bobBorrowedConnectionThread.start();

        // run session
        aliceDataSession = new DataExchangeTester(
                aliceBorrowedConnection.getInputStream(), aliceBorrowedConnection.getOutputStream(),
                rounds, "Alice on BC");

        bobDataSession = new DataExchangeTester(
                bobBorrowedConnection.getInputStream(), bobBorrowedConnection.getOutputStream(),
                rounds, "Bob on BC");

        aliceDataSessionThread = new Thread(aliceDataSession);
        bobDataSessionThread = new Thread(bobDataSession);

        System.out.println("+++++++++++++++++ start data session over borrowed connection ++++++++++++++++++");
        aliceDataSessionThread.start();
        bobDataSessionThread.start();

        // send some dirt in the stream
        byte[] someBytes = {0, 1, 2, 3, 4, 5, 6, 7};

        // wait a moment
        Thread.sleep(10);
        sideA_OS.write(someBytes);
        sideB_OS.write(someBytes);

        aliceBorrowedConnectionThread.join();
        bobBorrowedConnectionThread.join();
        System.out.println("+++++++++++++++++ data session over borrowed connection ended ++++++++++++++++++");

        System.out.println("+++++++++++++++++ simulate protocol again ++++++++++++++++++");
        aliceDataSession = new DataExchangeTester(sideA_IS, sideA_OS, rounds, "Alice");
        bobDataSession = new DataExchangeTester(sideB_IS, sideB_OS, rounds, "Bob");

        aliceDataSessionThread = new Thread(aliceDataSession);
        bobDataSessionThread = new Thread(bobDataSession);

        aliceDataSessionThread.start();
        bobDataSessionThread.start();
        System.out.println("+++++++++++++++++ blocked until test ends?! ++++++++++++++++++");

        Thread.sleep(1000);
    }

    @Test
    public void streamLinkTest() throws IOException, InterruptedException {
        int port = getPortNumber();
        SocketFactory socketFactory = new SocketFactory(new ServerSocket(port));
        Thread socketFactoryThread = new Thread(socketFactory);
        socketFactoryThread.start();

        // wait a moment to settle
        Thread.sleep(10);

        Socket socket1 = new Socket("localhost", port);
        /* side 1A <------> socket 1 <----------> side 1B */
        InputStream side1A_IS = socket1.getInputStream();
        OutputStream side1A_OS = socket1.getOutputStream();

        socketFactoryThread.join(); // wait until connection established
        InputStream side1B_IS = socketFactory.getInputStream();
        OutputStream side1B_OS = socketFactory.getOutputStream();

        port = getPortNumber();
        socketFactory = new SocketFactory(new ServerSocket(port));
        socketFactoryThread = new Thread(socketFactory);
        socketFactoryThread.start();

        // wait a moment to settle
        Thread.sleep(10);

        /* side 2A <------> socket 2 <----------> side 2B */
        Socket socket2 = new Socket("localhost", port);
        InputStream side2A_IS = socket2.getInputStream();
        OutputStream side2A_OS = socket2.getOutputStream();

        socketFactoryThread.join(); // wait until connection established
        InputStream side2B_IS = socketFactory.getInputStream();
        OutputStream side2B_OS = socketFactory.getOutputStream();

        /* side 1A_OS --> socket 1 --> side 1B_IS ==> side 2A_OS --> socket 2 --> side 2B_IS */
        StreamLink one2two = new StreamLink(side1B_IS, side2A_OS, maxIdleMillis, true, "Alice => Bob");
        one2two.start();
        /* side 1A_IS <-- socket 1 <-- side 1B_OS <== side 2A_IS <-- socket 2 <-- side 2B_OS */
        StreamLink two2one = new StreamLink(side2A_IS, side1B_OS, maxIdleMillis, true, "Alice <= Bob");
        two2one.start();

        // simulate protocol
        System.out.println("+++++++++++++++++ simulate protocol ++++++++++++++++++");
        int rounds = 5;
        DataExchangeTester aliceDataSession = new DataExchangeTester(side1A_IS, side1A_OS, rounds, "Alice");
        DataExchangeTester bobDataSession = new DataExchangeTester(side2B_IS, side2B_OS, rounds, "Bob");

        Thread aliceDataSessionThread = new Thread(aliceDataSession);
        Thread bobDataSessionThread = new Thread(bobDataSession);

        aliceDataSessionThread.start();
        bobDataSessionThread.start();

        // wait a moment
        Thread.sleep(1000);

        // close Alice side
        side1A_IS.close();

        one2two.join();
        two2one.join();
    }

    class DataExchangeTester implements Runnable {
        private final InputStream is;
        private final OutputStream os;
        private final String id;
        private int rounds;

        DataExchangeTester(InputStream is, OutputStream os, int rounds, String id) {
            this.is = is;
            this.os = os;
            this.rounds = rounds;
            this.id = id;
        }

        @Override
        public void run() {
            // exchange some example data
            int value = 0;
            try {
                while(this.rounds-- > 0) {
                    System.out.println("write data: " + value + " | " + id);
                    ASAPSerialization.writeIntegerParameter(value, this.os);
                    int retVal = ASAPSerialization.readIntegerParameter(this.is);

                    if(value != retVal) {
                        System.out.println("data exchange testers are out of sync: "+ id);
                        break;
                    }
                    value++;
                }
                // block
                System.out.println("blocking (?): "+ id);
                this.is.read();
                System.out.println("back from read(): "+ id);
            } catch (IOException | ASAPException e) {
                System.out.println("exception data exchange tester - most probably good: " + id + " | "
                        + e.getLocalizedMessage());
            }
        }
    }

    class SocketFactory implements Runnable {
        private final ServerSocket srv;
        InputStream is;
        OutputStream os;

        SocketFactory(ServerSocket srv) {
            this.srv = srv;
        }

        @Override
        public void run() {
            try {
                Socket socket = srv.accept();
                this.is = socket.getInputStream();
                this.os = socket.getOutputStream();
                System.out.println("socket created");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        public InputStream getInputStream() throws IOException {
            if(this.is == null) throw new IOException("no socket yet");
            return this.is;
        }

        public OutputStream getOutputStream() throws IOException {
            if(this.os == null) throw new IOException("no socket yet");
            return this.os;
        }
    }
}
