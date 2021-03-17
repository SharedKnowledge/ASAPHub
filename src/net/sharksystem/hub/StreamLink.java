package net.sharksystem.hub;

import net.sharksystem.utils.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

class StreamLink extends Thread {
    private final InputStream sourceIS;
    private final OutputStream targetOS;
    private final boolean closeStreams;
    private String id = "anon";

    StreamLink(InputStream sourceIS, OutputStream targetOS, long maxIdleInMillis, boolean closeStreams, String id) {
        this.sourceIS = sourceIS;
        this.targetOS = targetOS;
        this.closeStreams = closeStreams;
        this.id = id;
    }

    StreamLink(InputStream sourceIS, OutputStream targetOS, long maxIdleInMillis, boolean closeStreams) {
        this(sourceIS, targetOS, maxIdleInMillis, closeStreams, "no id");
    }

    public void run() {
        //Log.writeLog(this, "start read/write loop");
        try {
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
                    // block
                    //Log.writeLog(this, "going to block in read(): " + id);
                    read = sourceIS.read();
                    if(read != -1) {
                        targetOS.write(read);
                        again = true;
                    }
                }
            } while (again);
        } catch (IOException e) {
            Log.writeLog(this, "ioException - most probably connection closed: " + id);
        } finally {
            if(this.closeStreams) {
                try {this.targetOS.close();} catch (IOException ioException) { /* ignore */ }
                try {this.sourceIS.close();} catch (IOException ioException) { /* ignore */ }
            }

            Log.writeLog(this, "end connection: " + id);
        }
    }
}
