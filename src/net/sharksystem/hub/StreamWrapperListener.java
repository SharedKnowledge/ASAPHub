package net.sharksystem.hub;

public interface StreamWrapperListener {
    /** stream was closed */
    void notifyClosed();
    /** data read or written */
    void notifyAction();
}
