package net.sharksystem.hub;

import net.sharksystem.streams.StreamPair;
import net.sharksystem.streams.StreamPairWrapper;
import net.sharksystem.streams.WrappedStreamPairListener;
import net.sharksystem.asap.utils.Helper;
import net.sharksystem.hub.protocol.*;
import net.sharksystem.utils.AlarmClock;
import net.sharksystem.utils.AlarmClockListener;
import net.sharksystem.utils.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Root class for all connector implementation (hub and peer side) using a shared channel.
 */
public abstract class SharedChannelConnectorImpl extends ConnectorImpl
        implements AlarmClockListener, WrappedStreamPairListener {

    public SharedChannelConnectorImpl(InputStream is, OutputStream os) throws ASAPHubException {
        super(is, os);
    }

    public int getTimeOutSilenceChannel()  { return this.getTimeoutInMillis(); }
    public int getTimeOutDataConnection() {
        return this.getTimeoutInMillis();
    }
    public int getTimeOutConnectionRequest() { return this.getTimeoutInMillis() * 2; }

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
    public static final int ALARM_CLOCK_SYNC_TIMEOUT_SESSION = 3;

    AlarmClock askedForSilenceClock = null;
    AlarmClock inSilenceClock = null;
    AlarmClock dataSessionClock = null;
    AlarmClock syncTimeOutClock = null;

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
        if(this.syncTimeOutClock != null) {
            this.syncTimeOutClock.kill();
            this.syncTimeOutClock = null;
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
                if(this.wrappedDataSessionStreamPair != null) {
                    // this cannot be null...
                    this.wrappedDataSessionStreamPair.close();
                    //this.wrappedDataSessionStreamPair = null; do not null it! it is done in sync
                    this.enterSyncAfterDataSession();
                }
                break;

            case ALARM_CLOCK_SYNC_TIMEOUT_SESSION:
                Log.writeLog(this, this.toString(), "... ended: synchronization time out");
                this.syncTimeOutClock = null;
                Log.writeLog(this, this.toString(), "could not manage to get in sync with other side");
                this.fatalError();
                //this.dataSessionEnded();
                break;

            default: Log.writeLogErr(this, this.toString(), "unknown alarm clock was ringing: " + yourKey);
        }
    }

    protected boolean statusHubConnectorProtocol() {
        return !this.statusAskedForSilence()
                && !this.statusInSilence()
                && !this.statusInDataSession()
                && !statusSynchronizing;
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
//        return this.dataSessionClock != null;
        return this.wrappedDataSessionStreamPair != null;
    }

    private boolean statusSynchronizing = false;

    protected abstract void dataSessionStarted(ConnectionRequest connectionRequest, StreamPair streamPair);

    public final void askForSilence(long waitDuration) throws IOException, ASAPHubException {
        if(!this.statusHubConnectorProtocol()) throw new ASAPHubException("wrong status, cannot send silence RQ");
        if(askedForSilenceClock != null) this.askedForSilenceClock.kill(); // kill..
        this.askedForSilenceClock = new AlarmClock(waitDuration, ALARM_CLOCK_ASK_SILENCE, this); // ..and reset
        (new HubPDUSilentRQ(waitDuration)).sendPDU(this.getOutputStream());
        this.askedForSilenceClock.start();
    }

    public final void enterSilence(long waitDuration) throws ASAPHubException, IOException {
        if(!this.statusHubConnectorProtocol() && !this.statusAskedForSilence())
            throw new ASAPHubException("cannot enter silence mode - not in connector mode or asked for silence");

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

    private void enterDataSession(ConnectionRequest connectionRequest, int timeout) {
        if(!this.statusInSilence()) {
            Log.writeLogErr(this, this.toString(), "cannot enter data session - not in silence mode");
            return;
        }

        String sessionID = this.getID() + ":" + this.sessionCounter++;

        Log.writeLog(this, this.toString(), "start new data session " + sessionID);
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
        //TimedStreamPair timedStreamPair = new TimedStreamPair(this.wrappedDataSessionStreamPair, timeout);

        // tell sub classes
        this.dataSessionStarted(connectionRequest, this.wrappedDataSessionStreamPair);
    }

    private byte[] syncSequence;
    @Override
    public void channelClear(HubPDUChannelClear pdu) {
        // received clean channel pdu - launch data session
        if(!this.statusInSilence()) {
            Log.writeLogErr(this, this.toString(), "received clear pdu but not silenced");
        }

        // remember sync sequence
        this.syncSequence = pdu.syncSequence;
        /*
        Log.writeLog(this, this.toString(), "synch sequence: ");
        ASAPSerialization.printByteArray(this.syncSequence);
         */

        // launch data session

        this.enterDataSession(
                new ConnectionRequest(
                        pdu.sourcePeerID,
                        pdu.targetPeerID,
                        System.currentTimeMillis() + pdu.maxIdleInMillis),
                (int) pdu.maxIdleInMillis
        );
    }

    /**
     * Clear channel in order to start a data session
     * @param sourcePeerID
     * @param targetPeerID
     * @param timeout
     */
    private void clearChannel(CharSequence sourcePeerID, CharSequence targetPeerID, int timeout)
            throws IOException, ASAPHubException {
        if(!this.statusInSilence()) throw new ASAPHubException("not in silence mode - cannot clear channel");
        // create a sync sequence
        this.syncSequence = Helper.long2byteArray(System.currentTimeMillis());
        /*
        Log.writeLog(this, this.toString(), "sync sequence: ");
        ASAPSerialization.printByteArray(this.syncSequence);
         */

        HubPDUChannelClear channelClear = new HubPDUChannelClear(sourcePeerID, targetPeerID, timeout, syncSequence);
        channelClear.sendPDU(this.getOutputStream());
    }

    /*
    And here is the challenge:
    On both sides of the connection was a data session running. This session is ended by
    a) Peer closed the connection
    b) time out was fired on either side.

    We have no way to notify hub side about a closed connection - both side have to wait for data session time out.
    Now, there is a wrapped stream pair on either side. Closes set a flag and will notify reading threads about this
    closed stream. Most probably there will be a thread blocked in a read on both side. Not in all cases, though.

    A peer application might have closed the stream and stopped reading..

    Moreover... both time out clocks are not in perfect sync of course. It highly unlikely, but an application could
    send something in the stream when one side already closed the stream and the other did not. That would not be
    a problem if both time outs fire.  It is a serious problem if one side re-sets its time out but the other side
    assumes and end of the data session. We cannot recover from this final scenario. We can from the rest, though.

    Here is the sync algorithm: We already have a number of bytes on both sides due to the final CLEAR_CHANNEL PDU.
    We send those byte arrays twice. It is highly likely that at least the first byte gets lost - the wrapped thread
    comes back from read and realizes that it job is done. Unfortunately, it has already read the last byte from the
    input stream.

    In any case, the synchronization process try to figure out at what position it starts with the first series of bytes.
    We assume to be in sync again if we read the complete second series of bytes.
     */
    private void enterSyncAfterDataSession() {
        if(!this.statusInDataSession()) {
            Log.writeLogErr(this, this.toString(), "cannot enter sync after data session - not in data session mode");
            return;
        }

        Log.writeLog(this, this.toString(), "enter sync status after data session");

        this.stopAlarmClocks();
        this.statusSynchronizing = true;
        this.wrappedDataSessionStreamPair = null; // nullify it - sign: we are no longer in data session

        Log.writeLog(this, this.toString(), "wait a moment to ensure both ends stopped reading");
        try {
            Thread.sleep(this.getTimeOutSilenceChannel());
        } catch (InterruptedException e) {
            // ignore
        }
        Log.writeLog(this, this.toString(), "hope that both ends stopped reading - start cleaning up stream");

        if(this.syncSequence == null) {
            Log.writeLogErr(this, this.toString(), "internal error, no sync sequence defined");
            // go ahead and hope for the best
            this.syncedAfterDataSession();
            return;
        }

        // send synchronization sequence
        OutputStream os = this.getOutputStream();
        try {
            Log.writeLog(this, this.toString(), "write sync sequence");
            //os.write(this.syncSequence);

            // write sync sequence - which is bytes from 0 .. number. two times
            for(int round = 0; round < 2; round++)
                for (int i = 0; i < this.numberOfSyncBytes; i++) {
                    os.write(i);
                }
        } catch (IOException e) {
            Log.writeLogErr(this, this.toString(), "fatal: output stream closed");
            this.fatalError();
            return;
        }

        Log.writeLog(this, this.toString(), "start sync reader");
        (new SyncAfterDataSessionThread(this.toString())).start();
    }

    private int numberOfSyncBytes = 20;

    private class SyncAfterDataSessionThread extends Thread {
        private final String id;
        SyncAfterDataSessionThread(String id) { this.id = id; }
        public void run() {
            try {
                // sync with stream
                int lastInt = -1;
                int readInt = -1;
                boolean inCountUp = false;
                boolean secondRound = false;
                do {
                    readInt = SharedChannelConnectorImpl.this.getInputStream().read();
                    inCountUp = (lastInt > -1 && readInt == lastInt+1) || (readInt == 0 && lastInt == numberOfSyncBytes-1);
//                    Log.writeLog(this, SharedChannelConnectorImpl.this.toString(),"read " + readInt + " lastInt == " + lastInt + " inCountUp == " + inCountUp + " secondRound == " + secondRound);
                    if(readInt == 0 && inCountUp) secondRound = true;

                    lastInt = readInt;
                } while(readInt != numberOfSyncBytes-1 || !inCountUp || !secondRound); // last byte read
            } catch (IOException e) {
                // fatal
                SharedChannelConnectorImpl.this.fatalError();
                return;
            }
            Log.writeLog(this, this.id, "in sync again");
            SharedChannelConnectorImpl.this.syncedAfterDataSession();
        }
    }

    private void syncedAfterDataSession() {
        Log.writeLog(this, this.toString(), "synchronized status after data session");

        // synchronized again
        this.statusSynchronizing = false;

        this.actionWhenBackFromDataSession();
    }

    protected abstract void actionWhenBackFromDataSession();

    @Override
    public void notifyClosed(StreamPair closedStreamPair, String key) {
        Log.writeLog(this, this.toString(), "stream closed (" + key +  ")  - wait for time out to stay synced");
    }

    @Override
    public void notifyAction(String key) {
        // no action when data session notifies of something - only interested in close event
    }

    // TODO - take care of this ugly situation - connection broken to other side of this connector - shut all down
    private void fatalError() {
        Log.writeLogErr(this, this.toString(), "fatal: close streams (maybe already closed) and shutdown");
        try {
            InputStream inputStream = this.getInputStream();
            if(inputStream != null) inputStream.close();
        } catch (IOException e) {
            // ignore
        }
        try {
            OutputStream outputStream = this.getOutputStream();
            if(outputStream != null) outputStream.close();
        } catch (IOException e) {
            // ignore
        }

        // tell others
        this.shutdown();
    }

    abstract protected void shutdown();

    /////////////////////////////////////////////////////////////////////////////////////////////////////////////
    //                                       reaction on interface requests                                    //
    /////////////////////////////////////////////////////////////////////////////////////////////////////////////

    private StreamPairWrapper wrappedDataSessionStreamPair = null;
    private int sessionCounter = 0;

    public StreamPair initDataSession(CharSequence sourcePeerID, CharSequence targetPeerID, int timeout)
            throws ASAPHubException, IOException {

        return this.initDataSession(
            new ConnectionRequest(sourcePeerID, targetPeerID, System.currentTimeMillis() + timeout),
                timeout);
    }

    private Thread threadWaitingForDataConnection = null;

    synchronized protected StreamPair initDataSession(ConnectionRequest connectionRequest, int timeout)
            throws ASAPHubException, IOException {

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
        // clear channel
        this.clearChannel(connectionRequest.sourcePeerID, connectionRequest.targetPeerID, timeout);
        Log.writeLog(this, this.toString(), ".. cleared channel - launch data session");

        // launch data session and wait
        this.enterDataSession(connectionRequest, timeout);

        return this.wrappedDataSessionStreamPair;
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

    public String toString() {
        String status = null;
        if(statusHubConnectorProtocol()) status = "connected";
        else if(statusInSilence()) status = "silence";
        else if(statusAskedForSilence()) status = "askedForSilence";
        else if(statusInDataSession()) status = "dataSession";
        else if(statusSynchronizing) status = "syncing";
        return super.toString() + "|" + status;
    }
}
