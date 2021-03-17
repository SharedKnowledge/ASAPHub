package net.sharksystem.hub;

import net.sharksystem.utils.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

class StreamLink extends Thread {
    private final long maxIdleInMillis;
    private final InputStream sourceIS;
    private final OutputStream targetOS;
    private final boolean closeStreams;
    private String id = "anon";

    StreamLink(InputStream sourceIS, OutputStream targetOS, long maxIdleInMillis, boolean closeStreams, String id) {
        this.sourceIS = sourceIS;
        this.targetOS = targetOS;
        this.maxIdleInMillis = maxIdleInMillis;
        this.closeStreams = closeStreams;
        this.id = id;
    }

    StreamLink(InputStream sourceIS, OutputStream targetOS, long maxIdleInMillis, boolean closeStreams) {
        this(sourceIS, targetOS, maxIdleInMillis, closeStreams, "no id");
    }

    class AlarmClock extends Thread {
        private final Thread threadToWake;
        boolean timedOut;

        public AlarmClock(Thread threadToWake) {
            this.threadToWake = threadToWake;
        }

        @Override
        public void run() {
            boolean closeStreams = false;
            this.timedOut = false;
            try {
                Log.writeLog(this, "sleep " + maxIdleInMillis + ": " + id);
                Thread.sleep(maxIdleInMillis);
//                Log.writeLog(this, "woke up: " + id);
                // was not interrupted - close streams
                closeStreams = StreamLink.this.closeStreams;
                this.timedOut = true;
                if(this.threadToWake != null) {
//                    Log.writeLog(this, "interrupt: " + id);
                    this.threadToWake.interrupt();
                }
            } catch (InterruptedException e) {
                // stopped - no alarm - do nothing
                Log.writeLog(this, "interrupted: " + id);
            }

            if(closeStreams) {
                try {sourceIS.close();} catch (IOException e) { /* ignore */ }
                try {targetOS.close();} catch (IOException e) { /* ignore */ }
            }
        }
    }

    public void run() {
        //Log.writeLog(this, "start read/write loop");
        try {
            AlarmClock alarmClock = null;
            int read = -1;
            boolean again;
            do {
                again = false;
                int available = sourceIS.available();
                if (available > 0) {
                    byte[] buffer = new byte[available];
                    sourceIS.read(buffer);
                    targetOS.write(buffer);
                    again = true;
                } else {
                    // set alarm clock
                    /*
                    if (maxIdleInMillis > 0) {
                        alarmClock = new AlarmClock(Thread.currentThread());
                        alarmClock.start();
                    }
                     */

                    // block
                    long beforeBlock = System.currentTimeMillis();
                    Log.writeLog(this, "going to block in read(): " + id);
                    read = sourceIS.read();
                    long afterBlock = System.currentTimeMillis();

                    if(afterBlock - beforeBlock >= maxIdleInMillis) {
                        Log.writeLog(this, "waited longer than allowed " + id);
                        again = false;
                    } else {
                        Log.writeLog(this, "unblocked read within allowed time span " + id);
                        targetOS.write(read);
                        again = true;
                    }

                    /* back from read because:
                    a) read something
                    b) interrupted by alarm thread - does not work - tested it
                    c) streams closed by alarm thread

                    c) should result in a read == -1
                     */

                    // a) we read something
                    /*
                    if (alarmClock != null) {
                        alarmClock.interrupt();
                    }
                     */

                    // read something and time was not up.
//                    if(read != -1 && !alarmClock.timedOut) {
                    /*
                    if(read != -1) {
                        Log.writeLog(this, "read single sign: " + id);
                        targetOS.write(read);
                        again = true;
                    } else {
                        Log.writeLog(this, "read -1 or timed out: " + id);
                    }

                    alarmClock = null;
                     */
                }
            } while (again);
        } catch (IOException e) {
            try {
                if(this.closeStreams) this.targetOS.close();
            } catch (IOException ioException) {
            }
        }

        Log.writeLog(this, "end connection: " + id);
    }
}
