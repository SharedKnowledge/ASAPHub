package net.sharksystem.hub;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class HubSessionConnectionImpl implements HubSessionConnection {
    private final InputStream is;
    private final OutputStream os;
    private final CharSequence peerID;

    public HubSessionConnectionImpl(CharSequence peerID, InputStream is, OutputStream os) {
        this.peerID = peerID;
        this.is = is;
        this.os = os;
    }

    @Override
    public InputStream getInputStream() {
        return new InputStreamWrapper(this.is, this);
    }

    @Override
    public OutputStream getOutputStream() {
        return new OutputStreamWrapper(this.os, this);
    }

    @Override
    public CharSequence getPeerID() {
        return this.peerID;
    }

    @Override
    public void close() {
        // TODO
    }

    private class InputStreamWrapper extends InputStream {
        private final InputStream is;
        private final HubSessionConnectionImpl peerConnection;
        private boolean closed = false;

        InputStreamWrapper(InputStream is, HubSessionConnectionImpl peerConnection) {
            this.is = is;
            this.peerConnection = peerConnection;
        }

        @Override
        public int read() throws IOException {
            if(this.closed) throw new IOException("stream close");
            return this.is.read();
        }

        public void close() {
            // do not close the stream
            this.closed = true;
            this.peerConnection.close();
        }
    }

    private class OutputStreamWrapper extends OutputStream {
        private final OutputStream os;
        private final HubSessionConnectionImpl peerConnection;
        private boolean closed = false;

        OutputStreamWrapper(OutputStream os, HubSessionConnectionImpl peerConnection) {
            this.os = os;
            this.peerConnection = peerConnection;
        }

        @Override
        public void write(int value) throws IOException {
            if(this.closed) throw new IOException("stream close");
            this.os.write(value);
        }

        public void close() {
            // do not close the stream
            this.closed = true;
            this.peerConnection.close();
        }
    }
}
