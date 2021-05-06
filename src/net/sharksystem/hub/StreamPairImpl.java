package net.sharksystem.hub;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class StreamPairImpl extends StreamPairListenerManager implements StreamPair {
    private final InputStream is;
    private final OutputStream os;
    private final CharSequence peerID;

    public StreamPairImpl(InputStream is, OutputStream os, CharSequence peerID) {
        this.peerID = peerID;
        this.is = is;
        this.os = os;
    }

    @Override
    public InputStream getInputStream() { return this.is; }

    @Override
    public OutputStream getOutputStream() { return this.os; }

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

        this.notifyAllListenerClosed(this, this.peerID.toString());
    }
}
