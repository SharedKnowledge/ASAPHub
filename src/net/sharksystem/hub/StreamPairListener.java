package net.sharksystem.hub;

public interface StreamPairListener {
    /** stream was closed
     * @param key*/
    void notifyClosed(int key);
    /** data read or written
     * @param key*/
    void notifyAction(int key);
}
