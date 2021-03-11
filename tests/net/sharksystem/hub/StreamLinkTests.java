package net.sharksystem.hub;

import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;

public class StreamLinkTests {
    @Test
    public void linkTest() throws IOException, InterruptedException {

        new Thread(new SocketReaderTester(new ServerSocket(TestConstants.DEFAULT_PORT))).start();

        Socket socket = new Socket("localhost", TestConstants.DEFAULT_PORT);

        OutputStream outputStream = socket.getOutputStream();
        outputStream.write(42);
        outputStream.write(-1);

        Thread.sleep(Long.MAX_VALUE);
    }

    class SocketReaderTester implements Runnable {
        private final ServerSocket srv;

        SocketReaderTester(ServerSocket srv) {
            this.srv = srv;
        }

        @Override
        public void run() {
            try {
                Socket socket = srv.accept();
                InputStream inputStream = socket.getInputStream();
                while(true) inputStream.read();
            } catch (IOException e) {
                System.out.println("died");
            }
        }
    }
}
