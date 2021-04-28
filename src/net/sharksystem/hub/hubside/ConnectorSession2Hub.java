package net.sharksystem.hub.hubside;

import net.sharksystem.hub.ASAPHubException;
import net.sharksystem.hub.StreamPair;

import java.io.IOException;

public interface ConnectorSession2Hub {
    CharSequence getPeerID();

    /**
     * Ask connector session to send nothing to connector
     * @param duration
     */
    void silentRQ(int duration) throws IOException;

    boolean isSilent();

    StreamPair createDataConnection(CharSequence peerID, long maxIdleInMillis) throws IOException, ASAPHubException;

    void dataSessionEnded(StreamPair streamPair);
}
