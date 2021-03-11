package net.sharksystem.hub;

import net.sharksystem.utils.Log;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

class TwistedConnection extends Thread {
    private final ServerSocket srv1;
    private final ServerSocket srv2;
    private final int port1;
    private final int port2;
    private final long maxIdleInMillis;

    TwistedConnection(ServerSocket srv1, ServerSocket srv2) throws IOException {
        this(srv1, srv2, 0);
    }

    TwistedConnection(ServerSocket srv1, ServerSocket srv2, int maxIdleInSeconds) throws IOException {
        this.port1 = srv1.getLocalPort();
        this.port2 = srv2.getLocalPort();
        this.srv1 = srv1;
        this.srv2 = srv2;
        this.maxIdleInMillis = maxIdleInSeconds * 1000;
        Log.writeLog(this, "going to connect peer on port " + port1 + " | " + port2);
    }

    public void run() {
        // wait for both server sockets
        Wait4AcceptThread wait4AcceptThread1 = new Wait4AcceptThread(this.srv1);
        Wait4AcceptThread wait4AcceptThread2 = new Wait4AcceptThread(this.srv2);
        wait4AcceptThread1.start();
        wait4AcceptThread2.start();

        // wait for both threads to end
        try {
            wait4AcceptThread1.join();
            wait4AcceptThread2.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        if (wait4AcceptThread1.failed != null || wait4AcceptThread2.failed != null) {
            Log.writeLog(this, "server socket accept failed: "
                    + wait4AcceptThread1.failed + " | "
                    + wait4AcceptThread2.failed
            );
        } else {
            // both side connected
            Thread twistThread1 = null;
            try {
                twistThread1 = new StreamLink(
                        wait4AcceptThread1.socket.getInputStream(),
                        wait4AcceptThread2.socket.getOutputStream(),
                        this.maxIdleInMillis, true);

                Thread twistThread2 = new StreamLink(
                        wait4AcceptThread2.socket.getInputStream(),
                        wait4AcceptThread1.socket.getOutputStream(),
                        this.maxIdleInMillis, true);

                twistThread1.start();
                twistThread2.start();
            } catch (IOException e) {
                Log.writeLog(this, "problems starting data transfer: " + e.getLocalizedMessage());
            }
        }
    }

    private class Wait4AcceptThread extends Thread {
        private ServerSocket wait4AcceptSocket;
        private Socket socket = null;
        private IOException failed = null;

        Wait4AcceptThread(ServerSocket wait4AcceptSocket) {
            this.wait4AcceptSocket = wait4AcceptSocket;
        }

        public void run() {
            if(maxIdleInMillis > 0) {
                Thread timeOutThread = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            // kill server socket after a while
                            Thread.sleep(maxIdleInMillis);
                            if(wait4AcceptSocket != null) wait4AcceptSocket.close();
                        } catch (InterruptedException | IOException e) {
                            // ignore
                        }
                    }
                });
               timeOutThread.start();
            }

            try {
                this.socket = this.wait4AcceptSocket.accept();
                //Log.writeLog(this, "server socket accepted");
                this.wait4AcceptSocket.close();
            } catch (IOException e) {
                this.failed = e;
            }
            finally {
                this.wait4AcceptSocket = null; // avoid close attempt from killer thread
            }
        }
    }

}
