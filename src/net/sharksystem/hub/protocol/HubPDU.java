package net.sharksystem.hub.protocol;

import net.sharksystem.asap.ASAPException;
import net.sharksystem.asap.utils.ASAPSerialization;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public abstract class HubPDU {
    public static final byte HUB_REGISTER = 0;
    static final byte CONNECT_PEER_REQUEST = 1;
    public static final byte CONNECT_PEER_REPLY = 2;
    static final byte HUB_STATUS_REQUEST = 3;
    public static final byte HUB_STATUS_REPLY = 4;
    static final byte OPEN_NEW_TCP_SOCKET_RQ = 5;
    public static final byte SILENT_REQUEST = 6;
    static final byte SILENT_REPLY = 7;
    public static final byte CHANNEL_CLEAR = 8;
    static final byte HUB_UNREGISTER = 9;

    private final byte pduNumber;

    protected HubPDU(byte pduNumber) {
        this.pduNumber = pduNumber;
    }

    public byte getCommand() { return this.pduNumber; }

    public static HubPDU readPDU(InputStream is) throws IOException, ASAPException {
        byte b = ASAPSerialization.readByte(is);
        switch (b) {
            case HUB_REGISTER: return new HubPDURegister(is);
            case HUB_UNREGISTER: return new HubPDUUnregister(is);
            case CONNECT_PEER_REQUEST: return new HubPDUConnectPeerRQ(is);
            case HUB_STATUS_REQUEST: return new HubPDUHubStatusRQ(is);
            case HUB_STATUS_REPLY: return new HubPDUHubStatusRPLY(is);
            case OPEN_NEW_TCP_SOCKET_RQ: return new HubPDUConnectPeerNewTCPSocketRQ(is);
            case SILENT_REQUEST: return new HubPDUSilentRQ(is);
            case SILENT_REPLY: return new HubPDUSilentRPLY(is);
            case CHANNEL_CLEAR: return new HubPDUChannelClear(is);

            default: throw new IOException("unknown pdu type: " + b);
        }
    }

    public abstract void sendPDU(OutputStream os) throws IOException;

    void sendPDUNumber(OutputStream os) throws IOException {
        ASAPSerialization.writeByteParameter(pduNumber, os);

    }

    public String toString() {
        return "HubPDU #" + this.pduNumber + " | " + this.getClass().getSimpleName();
    }
}
