package net.sharksystem.hub.hubside;

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
    private final int timeOutConnectionRequest;
    private final int timeOutDataConnection;
    private final CharSequence sourcePeerID;
    private final CharSequence targetPeerID;

    NewConnectionCreator(ServerSocket srv, NewConnectionCreatorListener listener,
                         CharSequence sourcePeerID, CharSequence targetPeerID,
                         int timeOutConnectionRequest, int timeOutDataConnection) {
        this.srv = srv;
        this.listener = listener;
        this.sourcePeerID = sourcePeerID;
        this.targetPeerID = targetPeerID;
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

            this.listener.newConnectionCreated(this.sourcePeerID, this.targetPeerID,
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
