package net.sharksystem.hub;

import net.sharksystem.hub.peerside.HubConnectorStatusListener;
import net.sharksystem.hub.protocol.ConnectorThread;
import net.sharksystem.hub.protocol.HubPDU;
import net.sharksystem.utils.Log;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Root class of all connector implementations.
 */
public abstract class ConnectorImpl implements Connector {
    private ConnectorThread connectorThread = null;

    private final InputStream is;
    private final OutputStream os;

    private Set<HubConnectorStatusListener> statusListener = new HashSet<>();
    private int timeoutInMillis = DEFAULT_TIMEOUT_IN_MILLIS;

    public void addStatusListener(HubConnectorStatusListener listener) {
        this.statusListener.add(listener);
    }

    public void removeStatusListener(HubConnectorStatusListener listener) {
        this.statusListener.remove(listener);
    }

    private void notifyListenerConnectedAndOpen() {
        for(HubConnectorStatusListener listener : this.statusListener) {
            listener.notifyConnectedAndOpen();
        }
    }

    protected void notifyListenerSynced(boolean changed) {
        for(HubConnectorStatusListener listener : this.statusListener) {
            listener.notifySynced(this, changed);
        }
    }

    public void setTimeOutInMillis(int millis) {
        this.timeoutInMillis = millis;
    }

    public int getTimeoutInMillis() {
        return timeoutInMillis;
    }

    public ConnectorImpl(InputStream is, OutputStream os) throws ASAPHubException {
        this.is = is;
        this.os = os;

        if(this.is == null || this.os == null) throw new ASAPHubException("streams must not be null");
    }

    public OutputStream getOutputStream() {
        return this.os;
    }
    public InputStream getInputStream() {
        return this.is;
    }

    protected void pduNotHandled(HubPDU pdu) {
        Log.writeLog(this, "pdu is not handled in this implementation: " + pdu);
    }

    public void connectorSessionStarted(ConnectorThread connectorThread) {
        Log.writeLog(this, "connector thread running");
        this.connectorThread = connectorThread;
        this.resumedConnectorProtocol();
        this.notifyListenerConnectedAndOpen();
    }

    protected abstract void resumedConnectorProtocol();

    public void connectorSessionEnded(boolean noRecovery) {
        Log.writeLog(this, this.toString(), "connector thread ended");
        this.connectorThread = null;

        if(noRecovery) this.connectionLost();
    }

    /**
     * Permanent lost of connection
     */
    abstract protected void connectionLost();

    protected ConnectorThread getConnectorThread() throws ASAPHubException {
        if(this.connectorThread == null) throw new ASAPHubException("no connector thread");
        return this.connectorThread;
    }

    public abstract CharSequence getPeerID();

    Set<Byte> blockedCommands = new HashSet<>();
    List<Thread> blockedThreads = new ArrayList<>();

    public void notifyPDUReceived(HubPDU hubPDU) {
//        Log.writeLog(this, "remove block: " + hubPDU.getCommand() + " | " + this.blockedCommands);
        this.blockedCommands.remove(hubPDU.getCommand());
//        Log.writeLog(this, "removed block: " + this.blockedCommands);


        for(Thread blockedThread : this.blockedThreads) {
            blockedThread.interrupt();
        }
    }

    public void prepareBlockUntilReceived(byte pduCommand) {
//        Log.writeLog(this, "prepare block: " + pduCommand);
        this.blockedCommands.add(pduCommand);
//        Log.writeLog(this, "added block: " + this.blockedCommands);
    }

    public void blockUntilReceived(byte pduCommand) {
        Log.writeLog(this, "block: " + pduCommand);
        for(;;) {
            if (this.blockedCommands.contains(pduCommand)) {
                // block
                try {
                    this.blockedThreads.add(Thread.currentThread());
                    Log.writeLog(this, "wait: " + pduCommand);
                    Thread.sleep(this.timeoutInMillis);
                    Log.writeLog(this, "leave after timeout: " + pduCommand);
                    return;
                } catch (InterruptedException e) {
                    // woke up - do it again
                    Log.writeLog(this, "woke up from blocking " + pduCommand);
                }
            } else {
                Log.writeLog(this, "leave: " + pduCommand);
                return; // leave
            }
        }
    }

    // implement a default
    /*
    public void connectionRequest(CharSequence sourcePeerID, CharSequence targetPeerID, int timeout)
            throws ASAPHubException, IOException {
        this.connectionRequest(sourcePeerID, targetPeerID, timeout, false);
    }

    public abstract void connectionRequest(
            CharSequence sourcePeerID, CharSequence targetPeerID, int timeout, boolean newConnection) throws ASAPHubException, IOException;

     */

    private String idString = null;
    protected String getID() {
        if(this.idString == null) {
            String s;
            if (this.isHubSide()) {
                s = "hub ";
            } else {
                s = "peer ";
            }

            if(this.getPeerID() != null) {
                s = s + this.getPeerID().toString();
                this.idString = s; // peer registered - will not change
            } else {
                return s + "no peer yet";
            }
        }

        return this.idString;
    }

    public String toString() {
        return this.getID();
    }
}
