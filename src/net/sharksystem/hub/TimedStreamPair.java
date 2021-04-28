package net.sharksystem.hub;

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
    }

    public void startLending() {
        this.wasIdle = true;
        this.alarmClock = new AlarmClock(maxIdleInMillis, this);
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
