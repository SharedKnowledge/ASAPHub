package net.sharksystem.hub.hubside;

import net.sharksystem.hub.protocol.ConnectionRequest;
import net.sharksystem.streams.StreamPairImpl;
import net.sharksystem.utils.AlarmClock;
import net.sharksystem.utils.AlarmClockListener;
import net.sharksystem.utils.Log;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

class NewConnectionCreator extends Thread implements AlarmClockListener {
    private final ServerSocket srv;
    private final NewConnectionCreatorListener listener;
    private final ConnectionRequest connectionRequest;
    private final int timeOutConnectionRequest;
    private final int timeOutDataConnection;

    NewConnectionCreator(ServerSocket srv, NewConnectionCreatorListener listener, ConnectionRequest connectionRequest,
                         int timeOutConnectionRequest, int timeOutDataConnection) {
        this.srv = srv;
        this.listener = listener;
        this.connectionRequest = connectionRequest;
        this.timeOutConnectionRequest = timeOutConnectionRequest;
        this.timeOutDataConnection = timeOutDataConnection;
    }

    public void run() {
        try {
            // set alarm
            AlarmClock alarmClock = new AlarmClock(this.timeOutConnectionRequest, this);
            alarmClock.start();
            Socket newSocket = this.srv.accept();
            alarmClock.kill();
            Log.writeLog(this, "new connection initiated from peer side - setup data connection");

            this.listener.connectionCreated(
                    this.connectionRequest.sourcePeerID,
                    this.connectionRequest.targetPeerID,
                    StreamPairImpl.getStreamPair(newSocket.getInputStream(), newSocket.getOutputStream()));

        } catch (IOException e) {
            // maybe time out killed server socket.
            Log.writeLog(this, "accept failed: " + e.getLocalizedMessage());
        }
    }

    @Override
    public void alarmClockRinging(int i) {
        Log.writeLog(this, "timeout - close server port");
        try {
            this.srv.close();
        } catch (IOException e) {
            Log.writeLog(this, "problems closing server socket: " + e.getLocalizedMessage());
        }
    }
}
