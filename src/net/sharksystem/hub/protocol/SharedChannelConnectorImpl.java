package net.sharksystem.hub.protocol;

import net.sharksystem.hub.*;
import net.sharksystem.utils.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Root class for all connector implementation (hub and peer side) using a shared channel.
 */
public abstract class SharedChannelConnectorImpl extends ConnectorImpl
        implements AlarmClockListener, StreamPairListener {
    public static final int DEFAULT_SILENCE_TIME_OUT_IN_MILLIS = 100;
    public static final int DEFAULT_DATA_CONNECTION_TIME_OUT_IN_MILLIS = 100;
    public static final int DEFAULT_CONNECTION_REQUEST_TIME_OUT_IN_MILLIS = 100;

    private int timeOutSilenceChannel = DEFAULT_SILENCE_TIME_OUT_IN_MILLIS;
    private int timeOutDataConnection = DEFAULT_DATA_CONNECTION_TIME_OUT_IN_MILLIS;
    private int timeOutConnectionRequest = DEFAULT_CONNECTION_REQUEST_TIME_OUT_IN_MILLIS;

    public SharedChannelConnectorImpl(InputStream is, OutputStream os) throws ASAPHubException {
        super(is, os);
    }

    public int getTimeOutSilenceChannel() {
        return this.timeOutSilenceChannel;
    }
    public int getTimeOutDataConnection() {
        return this.timeOutDataConnection;
    }
    public int getTimeOutConnectionRequest() {
        return this.timeOutConnectionRequest;
    }

    /////////////////////////////////////////////////////////////////////////////////////////////////////////
    //                                         status management                                           //
    /////////////////////////////////////////////////////////////////////////////////////////////////////////
    /*
        There are several statuses
        i) Hub-Connector protocol engine is running. Wait for commands from connector/peer side
        ii) We asked peer side for silence (askedForSilence)
        iii) We are in silence mode (inSilence) - ready for data connection
        iv) We are in a data session (inDataSession) - initiated by peer side or hub
     */

    public static final int ALARM_CLOCK_ASK_SILENCE = 0;
    public static final int ALARM_CLOCK_CHANNEL_SILENCE = 1;
    public static final int ALARM_CLOCK_DATA_SESSION = 2;

    AlarmClock askedForSilenceClock = null;
    AlarmClock inSilenceClock = null;
    AlarmClock dataSessionClock = null;

    private void stopAlarmClocks() {
        if(this.askedForSilenceClock != null) {
            this.askedForSilenceClock.kill();
            this.askedForSilenceClock = null;
        }
        if(this.inSilenceClock != null) {
            this.inSilenceClock.kill();
            this.inSilenceClock = null;
        }
        if(this.dataSessionClock != null) {
            this.dataSessionClock.kill();
            this.dataSessionClock = null;
        }
    }

    // alarm clock rings
    public void alarmClockRinging(int yourKey) {
        Log.writeLog(this, this.toString(), "alarm clock is ringing...");
        switch (yourKey) {
            case ALARM_CLOCK_ASK_SILENCE:
                Log.writeLog(this, this.toString(), "... ended: asked for silence");
                this.askedForSilenceClock = null;
                break;

            case ALARM_CLOCK_CHANNEL_SILENCE:
                Log.writeLog(this, this.toString(), "... ended: channel silence");
                this.inSilenceClock = null;
                this.silenceEnded();
                break;

            case ALARM_CLOCK_DATA_SESSION:
                Log.writeLog(this, this.toString(), "... ended: data session");
                this.dataSessionClock = null;
                this.closeDataSessionStreamPair();
                this.dataSessionEnded();
                break;

            default: Log.writeLogErr(this, this.toString(), "unknown alarm clock was ringing: " + yourKey);
        }
    }

    protected boolean statusHubConnectorProtocol() {
        return !this.statusAskedForSilence() && !this.statusInSilence() && !this.statusInDataSession();
    }

    protected boolean statusAskedForSilence() {
        return this.askedForSilenceClock != null;
    }

    protected boolean statusInSilence() {
        return this.inSilenceClock != null;
    }
    protected abstract void silenceStarted();
    protected abstract void silenceEnded();

    protected boolean statusInDataSession() {
        return this.dataSessionClock != null;
    }
    protected abstract void dataSessionStarted(TimedStreamPair timedStreamPair);
    protected abstract void dataSessionEnded();

    public final void askForSilence(long waitDuration) throws IOException, ASAPHubException {
        if(!this.statusHubConnectorProtocol()) throw new ASAPHubException("wrong status, cannot send silence RQ");
        if(askedForSilenceClock != null) this.askedForSilenceClock.kill(); // kill..
        this.askedForSilenceClock = new AlarmClock(waitDuration, ALARM_CLOCK_ASK_SILENCE, this); // ..and reset
        (new HubPDUSilentRQ(waitDuration)).sendPDU(this.getOutputStream());
        this.askedForSilenceClock.start();
    }

    public final void enterSilence(long waitDuration) throws ASAPHubException, IOException {
        if(this.statusInDataSession()) throw new ASAPHubException("cannot enter silence mode - already in data session");
        if(askedForSilenceClock != null) this.askedForSilenceClock.kill(); // kill - we in silence now

        if(this.inSilenceClock != null) {
            this.inSilenceClock.kill(); // kill - we in silence now
            Log.writeLog(this, this.toString(), "already in silence mode - rewind clock");
        }

        this.inSilenceClock = new AlarmClock(waitDuration, ALARM_CLOCK_CHANNEL_SILENCE, this);
        this.inSilenceClock.start();

        // thread waiting for data connection?
        if(this.threadWaitingForDataConnection != null) {
            Log.writeLog(this, this.toString(), "wake thread that waits for data connection");
            this.threadWaitingForDataConnection.interrupt();
            this.threadWaitingForDataConnection = null;
            // and give this thread a chance to wake up
            try {
                Thread.sleep(1);
            } catch (InterruptedException e) {
                // ignore
            }
        }

        // tell sub classes
        this.silenceStarted();
    }

    private TimedStreamPair launchDataSession(int timeout) {
        String sessionID = this.getID() + ":" + this.sessionCounter++;

        Log.writeLog(this, this.toString(), "start new data session ");
        this.wrappedDataSessionStreamPair = new StreamPairWrapper(
                this.getInputStream(), this.getOutputStream(), this, sessionID);

        // kill connector thread
        try {
            ConnectorThread connectorThread = this.getConnectorThread();
            connectorThread.kill();
        } catch (ASAPHubException e) {
            // no connector thread - should not happen but wouldn't be bad - we kill it anyway
        }

        // kill all other alarm clocks
        this.stopAlarmClocks();

        // set alarm clock
        this.dataSessionClock = new AlarmClock(this.getTimeOutDataConnection(), ALARM_CLOCK_DATA_SESSION, this);
        this.dataSessionClock.start();

        // launch stream
        TimedStreamPair timedStreamPair = new TimedStreamPair(this.wrappedDataSessionStreamPair, timeout);

        // tell sub classes
        this.dataSessionStarted(timedStreamPair);

        return timedStreamPair;
    }

    private void closeDataSessionStreamPair() {
        if(this.wrappedDataSessionStreamPair != null) {
            this.wrappedDataSessionStreamPair.close();
            this.wrappedDataSessionStreamPair = null;
        }
    }

    @Override
    public void notifyClosed(String key) {
        Log.writeLog(this, this.toString(), "data session closed: " + key);
        this.wrappedDataSessionStreamPair = null;

        // relaunch Connector thread
        (new ConnectorThread(this, this.getInputStream())).start();

        // tell sub classes
        this.dataSessionEnded();
    }

    @Override
    public void notifyAction(String key) {
        // no action when data session notifies of something - only interested in close event
    }

    /////////////////////////////////////////////////////////////////////////////////////////////////////////////
    //                                       reaction on interface requests                                    //
    /////////////////////////////////////////////////////////////////////////////////////////////////////////////

    private StreamPairWrapper wrappedDataSessionStreamPair = null;
    private int sessionCounter = 0;

    public TimedStreamPair initDataSession(CharSequence sourcePeerID, CharSequence targetPeerID, int timeout)
            throws ASAPHubException, IOException {

        return this.initDataSession(
            new ConnectionRequest(sourcePeerID, targetPeerID, System.currentTimeMillis() + timeout),
                timeout);
    }

    private Thread threadWaitingForDataConnection = null;

    synchronized protected TimedStreamPair initDataSession(
            ConnectionRequest connectionRequest, int timeout) throws ASAPHubException, IOException {

        Log.writeLog(this, this.toString(), "try to init data session over shared channel..");
        if(!this.statusInSilence()) {
            Log.writeLog(this, this.toString(), "not in silence mode");
            if(this.threadWaitingForDataConnection == null) {
                Log.writeLog(this, this.toString(), "no other thread waiting - ask for silence and wait");
                this.threadWaitingForDataConnection = Thread.currentThread();
                this.askForSilence(timeout);
                try {
                    Thread.sleep(timeout);
                } catch (InterruptedException e) {

                }
            } else {
                throw new ASAPHubException("other thread waiting for data connection");
            }
            Log.writeLog(this, this.toString(), "waiting over");
            if(connectionRequest.until < System.currentTimeMillis()) {
                throw new ASAPHubException("timed out - will not create data connection");
            }

            if(!this.statusInSilence()) {
                throw new ASAPHubException("still no silent mode - cannot establish data connection with peer ");
            }
        }

        Log.writeLog(this, this.toString(), ".. we are in silence mode ..");
        // send clear message to the other side (peer)
        Log.writeLog(this, this.toString(), "send channel clear PDU: ");
        // tell connector we are ready
        HubPDUChannelClear channelClear = new HubPDUChannelClear(
                connectionRequest.sourcePeerID, connectionRequest.targetPeerID, timeout);

        channelClear.sendPDU(this.getOutputStream());

        Log.writeLog(this, this.toString(), ".. cleared channel - launch data session");

        // launch data session and wait
        return this.launchDataSession(timeout);
    }

    /////////////////////////////////////////////////////////////////////////////////////////////////////////
    //                                           send PDUs                                                 //
    /////////////////////////////////////////////////////////////////////////////////////////////////////////

    private void sendSilentRPLY(long waitDuration) {

    }

    /////////////////////////////////////////////////////////////////////////////////////////////////////////////
    //                                       reaction on received PDUs                                         //
    /////////////////////////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void silentRQ(HubPDUSilentRQ pdu) {
        try {
            this.enterSilence(pdu.waitDuration);
            // tell other side
            (new HubPDUSilentRPLY(pdu.waitDuration)).sendPDU(this.getOutputStream());
        } catch (IOException | ASAPHubException e) {
            Log.writeLogErr(this, this.toString(), "asking for silence failed: " + e.getLocalizedMessage());
        }
    }

    @Override
    public void silentRPLY(HubPDUSilentRPLY pdu) {
        try {
            this.enterSilence(pdu.waitDuration);
        } catch (IOException | ASAPHubException e) {
            Log.writeLogErr(this, this.toString(), "entering silence status failed: " + e.getLocalizedMessage());
        }
    }

    @Override
    public void channelClear(HubPDUChannelClear pdu) {
        // received clean channel pdu - launch data session
        if(!this.statusInSilence()) {
            Log.writeLogErr(this, this.toString(), "received clear pdu but not silenced");
        }

        this.launchDataSession((int) pdu.maxIdleInMillis);
    }

    public String toString() {
        String status = null;
        if(statusHubConnectorProtocol()) status = "connected";
        else if(statusInSilence()) status = "silence";
        else if(statusAskedForSilence()) status = "askedForSilence";
        else if(statusInDataSession()) status = "dataSession";
        return super.toString() + "|" + status;
    }
}
