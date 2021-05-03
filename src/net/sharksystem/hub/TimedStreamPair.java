package net.sharksystem.hub;

import net.sharksystem.utils.AlarmClock;
import net.sharksystem.utils.AlarmClockListener;
import net.sharksystem.utils.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class TimedStreamPair implements StreamPair, AlarmClockListener, StreamPairListener {
    private final StreamPairWrapper streamPairWrapper;
    private final long maxIdleInMillis;
    private AlarmClock alarmClock;
    private boolean wasIdle = true;
    private boolean closed = false;

    public TimedStreamPair(StreamPairWrapper streamPairWrapper, long maxIdleInMillis) {
        this.streamPairWrapper = streamPairWrapper;
        this.maxIdleInMillis = maxIdleInMillis;
        this.streamPairWrapper.addListener(this);
        this.startLending();
    }

    public void startLending() {
        if(this.alarmClock != null) this.alarmClock.kill();
        this.wasIdle = true;
        Log.writeLog(this, this.toString(), "timed stream pair launched, alarm in ms: " + this.maxIdleInMillis);
        this.alarmClock = new AlarmClock(this.maxIdleInMillis, this);
        this.alarmClock.start();
    }

    @Override
    public void alarmClockRinging(int yourKey) {
        if(this.closed || this.wasIdle) {
            Log.writeLog(this, this.toString(), "time out - alarm ringing");
            this.streamPairWrapper.close();
        } else {
            this.startLending();
        }
    }

    @Override
    public void notifyClosed(String key) {
        this.closed = true;
    }

    @Override
    public void notifyAction(String key) {
        this.wasIdle = false;
    }

    @Override
    public InputStream getInputStream() throws IOException {
        return this.streamPairWrapper.getInputStream();
    }

    @Override
    public OutputStream getOutputStream() throws IOException {
        return this.streamPairWrapper.getOutputStream();
    }

    @Override
    public void close() {
        this.streamPairWrapper.close();
    }

    public String toString() {
        return this.streamPairWrapper.toString();
    }
}
