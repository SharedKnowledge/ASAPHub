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

    StreamLink(InputStream sourceIS, OutputStream targetOS, long maxIdleInMillis, boolean closeStreams) {
        this.sourceIS = sourceIS;
        this.targetOS = targetOS;
        this.maxIdleInMillis = maxIdleInMillis;
        this.closeStreams = closeStreams;
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
                Thread.sleep(maxIdleInMillis);
                // was not interrupted - close streams
                closeStreams = StreamLink.this.closeStreams;
                this.timedOut = true;
                if(this.threadToWake != null) this.threadToWake.interrupt();
            } catch (InterruptedException e) {
                // stopped - no alarm - do nothing
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
                    if (maxIdleInMillis > 0) {
                        alarmClock = new AlarmClock(Thread.currentThread());
                        alarmClock.start();
                    }

                    // block
                    read = sourceIS.read();
                    /* back from read because:
                    a) read something
                    b) interrupted by alarm thread - does not work - tested it
                    c) streams closed by alarm thread

                    c) should result in a read == -1
                     */

                    // a) we read something
                    if (alarmClock != null) {
                        alarmClock.interrupt();
                    }

                    // read something and time was not up.
                    if(read != -1 && !alarmClock.timedOut) {
                        targetOS.write(read);
                        again = true;
                    }

                    alarmClock = null;
                }
            } while (again);
        } catch (IOException e) {
            try {
                if(this.closeStreams) this.targetOS.close();
            } catch (IOException ioException) {
            }
        }

        Log.writeLog(this, "end connection");
    }
}
