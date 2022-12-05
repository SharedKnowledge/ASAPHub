package net.sharksystem.hub;

import net.sharksystem.asap.ASAPException;

public class ASAPHubException extends ASAPException {
    public ASAPHubException() { super(); }
    public ASAPHubException(String message) {
        super(message);
    }
    public ASAPHubException(String message, Throwable cause) {
        super(message, cause);
    }
    public ASAPHubException(Throwable cause) {
        super(cause);
    }
}
