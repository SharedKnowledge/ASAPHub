package net.sharksystem.hub;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

public class StreamPairWrapper implements StreamPair {
    private final InputStreamWrapper is;
    private final OutputStreamWrapper os;
    private final int key;
    private List<StreamPairListener> listenerList = new ArrayList<>();

    public StreamPairWrapper(InputStream is, OutputStream os, StreamPairListener listener, int key) {
        this.is = new InputStreamWrapper(is);
        this.os = new OutputStreamWrapper(os);
        this.key = key;
        if(listener != null) {
            this.listenerList.add(listener);
        }

    }

    public StreamPairWrapper(InputStream is, OutputStream os) {
        this(is, os, null, 0);
    }

    @Override
    public InputStream getInputStream() {
        return this.is;
    }

    @Override
    public OutputStream getOutputStream() {
        return this.os;
    }

    @Override
    public void close() {
        // do not close the streams but prevent any further communication
        this.is.closed = true;
        this.os.closed = true;
        if(!this.listenerList.isEmpty()) {
            for(StreamPairListener listener : this.listenerList) {
                listener.notifyClosed(this.key);
            }
        }
    }

    private void notifyAction() {
        if(!this.listenerList.isEmpty()) {
            for(StreamPairListener listener : this.listenerList) {
                listener.notifyAction(this.key);
            }
        }
    }

    public void addListener(StreamPairListener listener) {

    }

    private class InputStreamWrapper extends InputStream {
        private final InputStream is;
        private boolean closed = false;

        InputStreamWrapper(InputStream is) {
            this.is = is;
        }

        @Override
        public int read() throws IOException {
            if(this.closed) throw new IOException("stream closed");
            int i = this.is.read();
            StreamPairWrapper.this.notifyAction();
            return i;
        }

        public void close() {
            StreamPairWrapper.this.close();
        }
    }

    private class OutputStreamWrapper extends OutputStream {
        private final OutputStream os;
        private boolean closed = false;

        OutputStreamWrapper(OutputStream os) {
            this.os = os;
        }

        @Override
        public void write(int value) throws IOException {
            if(this.closed) throw new IOException("stream closed");
            this.os.write(value);
            StreamPairWrapper.this.notifyAction();
        }

        public void close() {
            StreamPairWrapper.this.close();
        }
    }
}
