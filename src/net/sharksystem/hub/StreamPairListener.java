package net.sharksystem.hub;

public interface StreamPairListener {
    /** stream was closed
     * @param closedStreamPair
     * @param key*/
    void notifyClosed(StreamPair closedStreamPair, String key);
}
