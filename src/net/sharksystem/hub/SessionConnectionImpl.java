package net.sharksystem.hub;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class SessionConnectionImpl implements SessionConnection {
    private final InputStream is;
    private final OutputStream os;
    private final CharSequence peerID;

    public SessionConnectionImpl(InputStream is, OutputStream os, CharSequence peerID) {
        this.peerID = peerID;
        this.is = is;
        this.os = os;
    }

    @Override
    public InputStream getInputStream() { return this.is; }

    @Override
    public OutputStream getOutputStream() { return this.os; }

    @Override
    public CharSequence getPeerID() {
        return this.peerID;
    }

    @Override
    public void close() {
        try {
            is.close();
        } catch (IOException e) {
            // ignore
        }
        try {
            os.close();
        } catch (IOException e) {
            // ignore
        }
    }
}
