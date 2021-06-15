package net.sharksystem.hub.protocol;

import net.sharksystem.hub.ASAPHubException;
import net.sharksystem.hub.peerside.HubConnectorStatusListener;
import net.sharksystem.utils.Log;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashSet;
import java.util.Set;

/**
 * Root class of all connector implementations.
 */
public abstract class ConnectorImpl implements Connector {
    private ConnectorThread connectorThread = null;

    private final InputStream is;
    private final OutputStream os;

    private Set<HubConnectorStatusListener> statusListener = new HashSet<>();
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

    public void connectorSessionEnded() {
        Log.writeLog(this, this.toString(), "connector thread ended");
        this.connectorThread = null;
    }

    protected ConnectorThread getConnectorThread() throws ASAPHubException {
        if(this.connectorThread == null) throw new ASAPHubException("no connector thread");
        return this.connectorThread;
    }

    public abstract CharSequence getPeerID();

    private String idString = null;
    protected String getID() {
        if(this.idString == null) {
            if (this.isHubSide()) {
                this.idString = "hub ";
            } else {
                this.idString = "peer ";
            }

            this.idString = this.idString + this.getPeerID().toString();
        }
        return this.idString;
    }

    public String toString() {
        return this.getID();
    }
}
