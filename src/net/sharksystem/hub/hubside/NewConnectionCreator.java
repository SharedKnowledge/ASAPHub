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
            Log.writeLog(this, "start server socket / timeout (ms): " + this.timeOutConnectionRequest);
            AlarmClock alarmClock = new AlarmClock(this.timeOutConnectionRequest, this);
            alarmClock.start();
            Socket newSocket = this.srv.accept();
            alarmClock.kill();
            this.srv.close();
            Log.writeLog(this, "new connection initiated from peer side ("
                    + this.sourcePeerID + " --> " + this.targetPeerID + ")");
            Log.writeLog(this, "call listener: " + listener.getClass().getSimpleName());

            this.listener.newConnectionCreated(this.sourcePeerID, this.targetPeerID,
                    StreamPairImpl.getStreamPairWithSessionID(newSocket.getInputStream(), newSocket.getOutputStream(),
                            this.sourcePeerID + ":" + newSocket.getLocalPort()),
                    this.timeOutDataConnection);

        } catch (IOException e) {
            // maybe time out killed server socket.
            Log.writeLog(this, "accept failed: " + e.getLocalizedMessage());
        }
    }

    @Override
    public void alarmClockRinging(int i) {
        Log.writeLog(this, "timeout - close server port");
        try {
            if(this.srv != null) this.srv.close();
        } catch (IOException e) {
            Log.writeLog(this, "problems closing server socket: " + e.getLocalizedMessage());
        }
    }
}
