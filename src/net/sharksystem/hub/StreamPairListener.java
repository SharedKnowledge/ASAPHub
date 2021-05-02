package net.sharksystem.hub;

public interface StreamPairListener {
    /** stream was closed
     * @param key*/
    void notifyClosed(String key);
    /** data read or written
     * @param key*/
    void notifyAction(String key);
}
