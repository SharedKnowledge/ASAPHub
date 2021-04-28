package net.sharksystem.hub;

public class AlarmClock extends Thread {
    public static final int DEFAULT_KEY = 42;
    private final long duration;
    private final int key;
    private final AlarmClockListener listener;
    private Thread thread;

    public AlarmClock(long duration, int key, AlarmClockListener listener) {
        this.duration = duration;
        this.key = key;
        this.listener = listener;
    }

    public AlarmClock(long duration, AlarmClockListener listener) {
        this(duration, DEFAULT_KEY, listener);
    }

    public void kill() {
        if(this.thread != null) this.thread.interrupt();
    }

    public void run() {
        this.thread = Thread.currentThread();
        try {
            Thread.sleep(duration);
            this.listener.alarmClockRinging(this.key);
        } catch (InterruptedException e) {
            // woke up - do no tdo anything
        }
    }
}
