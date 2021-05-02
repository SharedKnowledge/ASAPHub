package net.sharksystem.hub;

import net.sharksystem.asap.ASAPException;
import net.sharksystem.asap.utils.ASAPSerialization;
import net.sharksystem.utils.Log;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * An existing pair of input and output stream shall temporarily be used by another process (a data session). Actual
 * user of this data session is unknown. It can (and most probably will) close a connection after finished. This must
 * not effect the established stream pair.
 *
 * Object of this class are threads. This threads terminates if no data are transmitted over a defined period of time.
 */
public class ObservedConnection extends Thread implements StreamPairListener, StreamPair {
    private static final int NUMBER_SYNC_SIGNS = 10;
    private final InputStreamWrapper wrappedIS;
    private final OutputStreamWrapper wrappedOS;
    private final String debugID;
    private final long maxIdleInMillis;
    private final InputStream borrowedIS;
    private final OutputStream borrowedOS;
    private IOException exception;

    public ObservedConnection(InputStream borrowedIS, OutputStream borrowedOS, CharSequence debugID, long maxIdleInMillis) {
        this.borrowedIS = borrowedIS;
        this.borrowedOS = borrowedOS;
        this.wrappedIS = new InputStreamWrapper(borrowedIS, this);
        this.wrappedOS = new OutputStreamWrapper(borrowedOS, this);
        this.debugID = debugID.toString();
        this.maxIdleInMillis = maxIdleInMillis;
    }

    ObservedConnection(InputStream borrowedIS, OutputStream borrowedOS, long maxIdleInMillis) {
        this(borrowedIS, borrowedOS, "untagged", maxIdleInMillis);
    }

    private List<Thread> waitingThreads = new ArrayList<>();
    private boolean readyForLender = false;

    private void checkReadyForLender() {
        if(!readyForLender) {
            // let thread wait
            this.waitingThreads.add(Thread.currentThread());
            try {
                Log.writeLog(this, "connection not yet established - thread waits");
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
        Log.writeLog(this, "synchronising with counterpart..: " + this);

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
//                Log.writeLog(this, "create a random int");
                long seed = this.hashCode() * System.currentTimeMillis();
                Random random = new Random(seed);

                localFirstSyncInt = random.nextInt();
                //Log.writeLog(this, "flip and take number " + localFirstSyncInt);
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
        boolean iAmFirst = localFirstSyncInt > remoteFirstSyncInt;

        /*
        Log.writeLog(this, "synchronised with maxMillis | sync number local|remote: " + this + " | "
                + negotiatedMaxIdleInMillis + " | " + localSyncSign + " | " + remoteSyncSign);
         */

        try {
            //Log.writeLog(this, "take a nap (half of max idle time) to allow both side to settle: " + this);

            Thread.sleep(this.maxIdleInMillis / 2);
        } catch (InterruptedException e) {
            // ignore
        }

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
//                Log.writeLog(this, "next lending time (in ms): " + newSleepingTime);
                // remember when started sleeping
                long bedtime = System.currentTimeMillis();
                Thread.sleep(newSleepingTime);
                newSleepingTime = this.lastAction - bedtime;
                if(this.lastAction == 0 || newSleepingTime <= 0) {
                    // nothing happened at all or is longer ahead than max waiting time
                    Log.writeLog(this, "max time millis exceeded / close stream wrapper: " + this);
                    this.close(); // do not allow process to used borrowed streams any longer
                    break;
                }
            } catch (InterruptedException e) {
                // interrupted
                Log.writeLog(this, "interrupted (most prob. closed) / wait timeout period: " + this);
                this.close(); // do not allow process to used borrowed streams any longer
                try {
                    // other side cannot know about closed streams - this side must wait it out
                    Thread.sleep(negotiatedMaxIdleInMillis);
                } catch (InterruptedException interruptedException) {
                    // ignore
                }
                break;
            }
        } while(!this.terminated);
        Log.writeLog(this, "take back lent streams: " + this);

        ////////////////////// re-take streams
        /* at this moment: both sides are out of data session mode and willing to sync
        * Idea: Thank to our negotiation we can decide what sides starts. This side sends
        * a serious of same bytes. Other sides sends back.
        *
        * Problem: We do not know exactly when each party enters this state. We must ensure that neither process blocks

        boolean beaconActive = true;
        byte anotherSign = (byte) (localSyncSign & remoteSyncSign);

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Log.writeLog(BorrowedConnection.this, "start beacon with " + anotherSign);
                    while(beaconActive) {
                            borrowedOS.write(anotherSign); // any sign - just write something
                    }
                } catch (IOException e) {
                    // ignore
                }
                Log.writeLog( BorrowedConnection.this, "beacon ended");
            }
        }).start();
        *
        * */

        try {
            int counter = 0;
            boolean send;
            long sumDiff = 0;
            long lastArrival = 0;

            byte expectedSign = iAmFirst ? localSyncSign : remoteSyncSign;
            byte unexpectedSign = iAmFirst ? remoteSyncSign : localSyncSign;
            send = iAmFirst;

            Log.writeLog(this, "start sync round: "
                    + "expectedSign == " + expectedSign
                    + " | send == " + send
                    + " | iAmFirst == " + iAmFirst
                    + ": " + this
            );

            // case: Comes second, other is sender but is already blocked in read(): write at least one sign to unblock
            this.borrowedOS.write(unexpectedSign);
            //Log.writeLog(this, "sent == " + unexpectedSign + " : " + this);
            while(counter < NUMBER_SYNC_SIGNS) {
                if(send) {
                    // fill stream
                    this.borrowedOS.write(expectedSign);
                    //Log.writeLog(this, "write expected sign: " + this);
                }

                int readSign = this.borrowedIS.read();
                if(readSign == -1) throw new IOException("read -1 - stream gone");
                byte byteSign = (byte) readSign;
                Log.writeLog(this, "read == " + byteSign + " | counter == " + counter + " : " + this);
                if (byteSign == expectedSign) {
                    long now = System.currentTimeMillis();
                    counter++;
                    this.borrowedOS.write(expectedSign);
                    if(lastArrival > 0) {
                        sumDiff += now - lastArrival;
                    }
                    lastArrival = now;
                    //Log.writeLog(this, "reply expected sign: " + this);
                } else {
                    //Log.writeLog(this, "reset counter " + this);
                    counter = 0;
                    lastArrival = 0; sumDiff = 0;
                }
            }

            long avgDiff = sumDiff / counter;

            // Weird and I am not really sure - sometimes a single byte gets lost... this fixes it
            /*for(int i = 0; i < NUMBER_SYNC_SIGNS; i++)*/ this.borrowedOS.write(expectedSign);
            this.borrowedOS.write(unexpectedSign);

            Log.writeLog(this, "synchronised (sum diff in ms: " + sumDiff + ") - empty input stream: " + this);
            while(this.borrowedIS.available() > 0) {
                this.borrowedIS.read();
            }

            Log.writeLog(this, "wait " + sumDiff + " ms to give other side time to settle " + this);
            try {
                Thread.sleep(sumDiff);
            } catch (InterruptedException e) {
                // ignore
            }

        } catch (IOException e) {
            // end give this thread back - borrowed streams are most probably closed
            Log.writeLog(this, "ioException on borrowed streams - give up: " + this);
            this.exception = e;
            //e.printStackTrace();
        }

        // streams are empty now
        Log.writeLog(this, "thread ended: " + this);
        // end thread
    }

    private long lastAction = 0;

    @Override
    public void notifyClosed(String key) {
        this.close();
    }

    public void notifyAction(String key) {
        this.lastAction = System.currentTimeMillis();
    }

    public String toString() {
        return this.debugID;
    }

    public long getMaxIdleInMillis() {
        return this.maxIdleInMillis;
    }

    private class InputStreamWrapper extends InputStream {
        private final InputStream is;
        private final StreamPairListener listener;
        private boolean closed = false;

        InputStreamWrapper(InputStream is, StreamPairListener listener) {
            this.is = is;
            this.listener = listener;
        }

        @Override
        public int read() throws IOException {
            if (this.closed) throw new IOException("stream close");
            int i = this.is.read();
            this.listener.notifyAction("42");
            if(terminated) throw new IOException("stream closed");
            return i;
        }

        public void close() {
            if (this.closed) return;
            this.closed = true;
            this.listener.notifyClosed("42");
        }
    }

    private class OutputStreamWrapper extends OutputStream {
        private final OutputStream os;
        private final StreamPairListener listener;
        private boolean closed = false;

        OutputStreamWrapper(OutputStream os, StreamPairListener listener) {
            this.os = os;
            this.listener = listener;
        }

        @Override
        public void write(int value) throws IOException {
            if (this.closed) throw new IOException("stream close");
            this.os.write(value);
            this.listener.notifyAction("42");
        }

        public void close() {
            if (this.closed) return;
            this.closed = true;
            this.listener.notifyClosed("42");
        }
    }
}
