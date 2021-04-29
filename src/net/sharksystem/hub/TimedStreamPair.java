package net.sharksystem.hub;

import net.sharksystem.utils.Log;

public class TimedStreamPair implements AlarmClockListener, StreamPairListener {
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
        Log.writeLog(this, "timed stream pair launched, alarm in ms: " + this.maxIdleInMillis);
        this.alarmClock = new AlarmClock(this.maxIdleInMillis, this);
        this.alarmClock.start();
    }

    @Override
    public void alarmClockRinging(int yourKey) {
        if(this.closed || this.wasIdle) {
            this.streamPairWrapper.close();
        } else {
            this.startLending();
        }
    }

    @Override
    public void notifyClosed(int key) {
        this.closed = true;
    }

    @Override
    public void notifyAction(int key) {
        this.wasIdle = false;
    }
}
