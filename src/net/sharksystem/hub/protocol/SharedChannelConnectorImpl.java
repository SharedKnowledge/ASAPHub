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

    // alarm clock rings
    public void alarmClockRinging(int yourKey) {
        Log.writeLog(this, "alarm clock is ringing...");
        switch (yourKey) {
            case ALARM_CLOCK_ASK_SILENCE:
                Log.writeLog(this, "... ended: asked for silence");
                this.askedForSilenceClock = null;
                break;

            case ALARM_CLOCK_CHANNEL_SILENCE:
                Log.writeLog(this, "... ended: channel silence");
                this.inSilenceClock = null;
                this.silenceEnded();
                break;

            case ALARM_CLOCK_DATA_SESSION:
                Log.writeLog(this, "... ended: data session");
                this.dataSessionClock = null;
                this.closeDataSessionStreamPair();
                this.dataSessionEnded();
                break;

            default: Log.writeLogErr(this, "unknown alarm clock was ringing: " + yourKey);
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
        this.askedForSilenceClock.start();
    }

    public final void enterSilence(long waitDuration) throws ASAPHubException, IOException {
        if(this.statusInDataSession()) throw new ASAPHubException("cannot enter silence mode - already in data session");
        if(askedForSilenceClock != null) this.askedForSilenceClock.kill(); // kill - we in silence now

        if(this.inSilenceClock != null) {
            this.inSilenceClock.kill(); // kill - we in silence now
            Log.writeLog(this, "already in silence mode - rewind clock");
        }

        this.inSilenceClock = new AlarmClock(waitDuration, ALARM_CLOCK_CHANNEL_SILENCE, this);
        this.inSilenceClock.start();
        // tell sub classes
        this.silenceStarted();
    }

    public void clearChannel(ConnectionRequest connectionRequest, int timeout) throws IOException, ASAPHubException {
        // re-start alarm clock
        this.enterSilence(this.timeOutSilenceChannel);

        // send channel clear pdu
        Log.writeLog(this, "send channel clear PDU: ");
        // tell connector we are ready
        HubPDUChannelClear channelClear = new HubPDUChannelClear(
                connectionRequest.sourcePeerID, connectionRequest.targetPeerID, timeout);

        channelClear.sendPDU(this.getOutputStream());
    }

    private TimedStreamPair launchDataSession(int timeout) {
        int sessionID = this.sessionCounter++;
        Log.writeLog(this, "start new session: ");
        this.wrappedDataSessionStreamPair = new StreamPairWrapper(
                this.getInputStream(), this.getOutputStream(), this, sessionID);

        // kill connector thread
        try {
            ConnectorThread connectorThread = this.getConnectorThread();
            connectorThread.kill();
        } catch (ASAPHubException e) {
            // no connector thread - should not happen but wouldn't be bad - we kill it anyway
        }

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
        this.wrappedDataSessionStreamPair.close();
        this.wrappedDataSessionStreamPair = null;
    }

    @Override
    public void notifyClosed(int key) {
        Log.writeLog(this, "data session closed: " + key);
        this.wrappedDataSessionStreamPair = null;

        // relaunch Connector thread
        (new ConnectorThread(this, this.getInputStream())).start();

        // tell sub classes
        this.dataSessionEnded();
    }

    @Override
    public void notifyAction(int key) {
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

    synchronized protected TimedStreamPair initDataSession(
            ConnectionRequest connectionRequest, int timeout) throws ASAPHubException, IOException {

        Log.writeLog(this, "try to init data session over shared channel..");
        if(!this.statusInSilence()) throw new ASAPHubException("not in silence mode - cannot initiate data session");

        Log.writeLog(this, ".. we are in silence mode ..");
        // send clear message to the other side (peer)
        this.clearChannel(connectionRequest, timeout);

        Log.writeLog(this, ".. cleared channel - launch data session");

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
            this.askForSilence(pdu.waitDuration);
            // tell other side
            (new HubPDUSilentRPLY(pdu.waitDuration)).sendPDU(this.getOutputStream());
        } catch (IOException | ASAPHubException e) {
            Log.writeLogErr(this, "asking for silence failed: " + e.getLocalizedMessage());
        }
    }

    @Override
    public void silentRPLY(HubPDUSilentRPLY pdu) {
        try {
            this.enterSilence(pdu.waitDuration);
        } catch (IOException | ASAPHubException e) {
            Log.writeLogErr(this, "entering silence status failed: " + e.getLocalizedMessage());
        }
    }

    @Override
    public void channelClear(HubPDUChannelClear pdu) {
        // received clean channel pdu - launch data session
        if(!this.statusInSilence()) {
            Log.writeLogErr(this, "received clear pdu but not silenced");
        }

        this.launchDataSession((int) pdu.maxIdleInMillis);
    }
}
