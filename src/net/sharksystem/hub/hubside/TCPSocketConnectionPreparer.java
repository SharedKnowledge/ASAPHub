package net.sharksystem.hub.hubside;

import net.sharksystem.hub.ConnectionPreparer;
import net.sharksystem.streams.StreamPair;
import net.sharksystem.utils.Log;

import java.io.IOException;

class TCPSocketConnectionPreparer implements ConnectionPreparer {
    @Override
    public void prepare(StreamPair streamPair) {
        try {
            streamPair.getOutputStream().write(ConnectionPreparer.readyByte);
        } catch (IOException e) {
            Log.writeLog(this, "socket already gone before first usage");
        }
    }
}
