package net.sharksystem.hub;

import net.sharksystem.asap.ASAPException;
import net.sharksystem.asap.utils.ASAPSerialization;
import net.sharksystem.utils.Log;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * An existing pair of input and output stream shall temporary be used by another process (a data session). Actual
 * user of this data session is unknown. It can (and most probably will) close a connection after finished. This must
 * not effect the established stream pair.
 *
 * Object of this class are threads. This threads terminates if no data are transmitted over a defined period of time.
 */
class BorrowedConnection extends Thread implements StreamWrapperListener {
    private static final int NUMBER_SYNC_SIGNS = 10;
    private final InputStreamWrapper wrappedIS;
    private final OutputStreamWrapper wrappedOS;
    private final String debugID;
    private final long maxIdleInMillis;
    private final InputStream borrowedIS;
    private final OutputStream borrowedOS;
    private IOException exception;

    BorrowedConnection(InputStream borrowedIS, OutputStream borrowedOS, CharSequence debugID, long maxIdleInMillis) {
        this.borrowedIS = borrowedIS;
        this.borrowedOS = borrowedOS;
        this.wrappedIS = new InputStreamWrapper(borrowedIS, this);
        this.wrappedOS = new OutputStreamWrapper(borrowedOS, this);
        this.debugID = debugID.toString();
        this.maxIdleInMillis = maxIdleInMillis;
    }

    BorrowedConnection(InputStream borrowedIS, OutputStream borrowedOS, long maxIdleInMillis) {
        this(borrowedIS, borrowedOS, "untagged", maxIdleInMillis);
    }

    private List<Thread> waitingThreads = new ArrayList<>();
    private boolean readyForLender = false;

    private void checkReadyForLender() {
        if(!readyForLender) {
            // let thread wait
            this.waitingThreads.add(Thread.currentThread());
            try {
                Thread.sleep(Long.MAX_VALUE);
            } catch (InterruptedException e) {
                // back
            }
        }
    }

    public InputStream getInputStream() throws IOException {
        this.checkReadyForLender();
        if(terminated) throw new IOException("cannot establish connection");
        return this.wrappedIS;
    }

    public OutputStream getOutputStream() throws IOException {
        this.checkReadyForLender();
        if(terminated) throw new IOException("cannot establish connection");
        return this.wrappedOS;
    }

    public void close()  {
        this.wrappedIS.close();
        this.wrappedOS.close();
        this.terminate();
    }

    private void terminate() {
        this.terminated = true;
        if(waitThread != null) this.waitThread.interrupt();
    }

    private void wakeWaitingThreads() {
        for(Thread waitingThread : this.waitingThreads) {
            waitingThread.interrupt();
        }
    }

    private boolean terminated = false;
    private Thread waitThread = null;

    public void run() {
        Log.writeLog(this, "started synchronising " + this);

        long negotiatedMaxIdleInMillis = 0;
        int localFirstSyncInt = 0, remoteFirstSyncInt = 0;
        // sync before
        try {
            ASAPSerialization.writeLongParameter(this.maxIdleInMillis, this.borrowedOS);
            negotiatedMaxIdleInMillis = ASAPSerialization.readLongParameter(this.borrowedIS);
            if(negotiatedMaxIdleInMillis < this.maxIdleInMillis) {
                negotiatedMaxIdleInMillis = this.maxIdleInMillis;
            }

            long receivedMaxIdle;
            do {
                ASAPSerialization.writeLongParameter(negotiatedMaxIdleInMillis, this.borrowedOS);
                receivedMaxIdle = ASAPSerialization.readLongParameter(this.borrowedIS);
            } while(negotiatedMaxIdleInMillis != receivedMaxIdle);

            // negotiated max idle - negotiate different numbers to synchronize after the session

            int counter = 0;
            do {
                Log.writeLog(this, "create a random int");
                long seed = this.hashCode() * System.currentTimeMillis();
                Random random = new Random(seed);

                localFirstSyncInt = random.nextInt();
                Log.writeLog(this, "flip and take number " + localFirstSyncInt);
                ASAPSerialization.writeIntegerParameter(localFirstSyncInt, this.borrowedOS);
                remoteFirstSyncInt = ASAPSerialization.readIntegerParameter(this.borrowedIS);
                counter++;
            } while(localFirstSyncInt == remoteFirstSyncInt && counter < 100);
            if(counter >= 100) {
                Log.writeLogErr(this, "unable to negotiate different numbers - give up");
                return;
            }
        } catch (IOException | ASAPException e) {
            Log.writeLogErr(this, "Exception when synchronising - fatal");
            e.printStackTrace();
            return;
        }

        // prepare synchronisation after borrowed session
        // init first
        byte localSyncSign = (byte) localFirstSyncInt; // cut it to byte
        byte remoteSyncSign = (byte) remoteFirstSyncInt; // cut it to byte

        Log.writeLog(this, "synchronised with maxMillis | sync number local|remote: " + this + " | "
                + negotiatedMaxIdleInMillis + " | " + localSyncSign + " | " + remoteSyncSign);

        ////////////////////////////////// start borrowing streams
        this.readyForLender = true;
        // wake waiting threads - if any
        this.wakeWaitingThreads();

        this.waitThread = Thread.currentThread();
        long newSleepingTime = negotiatedMaxIdleInMillis;
        this.lastAction = 0;
        Log.writeLog(this, "start lending streams: " + this);
        do {
            try {
                Log.writeLog(this, "next lending time (in ms): " + newSleepingTime);
                // remember when started sleeping
                long bedtime = System.currentTimeMillis();
                Thread.sleep(newSleepingTime);
                newSleepingTime = this.lastAction - bedtime;
                if(this.lastAction == 0 || newSleepingTime <= 0) {
                    // nothing happened at all or is longer ahead than max waiting time
                    this.close(); // do not allow process to used borrowed streams any longer
                    break;
                }
            } catch (InterruptedException e) {
                // interrupted
                break;
            }
        } while(!this.terminated);
        Log.writeLog(this, "take back lent streams: " + this);

        ////////////////////// re-take streams

        // but first - sleep a little moment to increase the chance that both sides are run the cleaning protocol.
        try {
            Log.writeLog(this, "another nap (half of max idle time) to ensure lender came to an end: "
                    + this);

            Thread.sleep(this.maxIdleInMillis / 2);
        } catch (InterruptedException e) {
            // ignore
        }

        try {
            boolean remoteSyncSignRead = false;
            boolean localSyncSignRead = false;
            int readInt = 0;
            boolean readBefore = false;

            // send a sign into stream to unblock a borrow readers
            this.borrowedOS.write(localSyncSign);

            // first round - read remain bytes from stream if any - make sure not to block
            while(this.borrowedIS.available() > 0) {
                readInt = this.borrowedIS.read();
                readBefore = true;
            }
            Log.writeLog(this, "done reading remaining bytes from stream: " + this);

            ////////////////////// ensure there is my counterpart talking on the other side & get in sync with it

            // write local sync sign again
            this.borrowedOS.write(localSyncSign);

            do {
                if(!readBefore) {
                    readInt = this.borrowedIS.read();
                }

                readBefore = false;
                byte readByte = (byte) readInt;
                if(readByte == localSyncSign) {
                    Log.writeLog(this, "local sync sign read: " + this);
                    localSyncSignRead = true;
                }
                else if(readByte == remoteSyncSign) {
                    Log.writeLog(this, "remote sync sign read: " + this);
                    remoteSyncSignRead = true;
                    this.borrowedOS.write(remoteSyncSign);
                }
            } while(!localSyncSignRead || !remoteSyncSignRead);

            // both sync signs read - we are in sync

            // just in case
            Log.writeLog(this, "final check on remaining signs on stream: " + this);
            while(this.borrowedIS.available() > 0) {
                this.borrowedIS.read();
            }

            // streams are empty now
            Log.writeLog(this, "synchronised: " + this);
        } catch (IOException e) {
            // end give this thread back - borrowed streams are most probably closed
            Log.writeLog(this, "ioException on borrowed streams - give up: " + this);
            this.exception = e;
        }

        // end thread
    }

    private long lastAction = 0;

    @Override
    public void notifyClosed() {
        this.close();
    }

    public void notifyAction() {
        this.lastAction = System.currentTimeMillis();
    }

    public String toString() {
        return this.debugID;
    }

    private class InputStreamWrapper extends InputStream {
        private final InputStream is;
        private final StreamWrapperListener listener;
        private boolean closed = false;

        InputStreamWrapper(InputStream is, StreamWrapperListener listener) {
            this.is = is;
            this.listener = listener;
        }

        @Override
        public int read() throws IOException {
            if (this.closed) throw new IOException("stream close");
            int i = this.is.read();
            this.listener.notifyAction();
            if(terminated) throw new IOException("stream closed");
            return i;
        }

        public void close() {
            if (this.closed) return;
            this.closed = true;
            this.listener.notifyClosed();
        }
    }

    private class OutputStreamWrapper extends OutputStream {
        private final OutputStream os;
        private final StreamWrapperListener listener;
        private boolean closed = false;

        OutputStreamWrapper(OutputStream os, StreamWrapperListener listener) {
            this.os = os;
            this.listener = listener;
        }

        @Override
        public void write(int value) throws IOException {
            if (this.closed) throw new IOException("stream close");
            this.os.write(value);
            this.listener.notifyAction();
        }

        public void close() {
            if (this.closed) return;
            this.closed = true;
            this.listener.notifyClosed();
        }
    }
}
