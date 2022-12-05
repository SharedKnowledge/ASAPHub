package net.sharksystem.hub.protocol;

import net.sharksystem.asap.utils.DateTimeHelper;

public class ConnectionRequest {
    public final CharSequence sourcePeerID;
    public final CharSequence targetPeerID;
    public final long until;
    public final boolean newConnection;

    public ConnectionRequest(CharSequence sourcePeerID, CharSequence targetPeerID, long until, boolean newConnection) {
        this.sourcePeerID = sourcePeerID;
        this.targetPeerID = targetPeerID;
        this.until = until;
        this.newConnection = newConnection;
    }

    public String toString() {
        StringBuffer sb = new StringBuffer();
        sb.append("sourceId: ");
        sb.append(this.sourcePeerID);
        sb.append(" | ");
        sb.append("targetId: ");
        sb.append(this.targetPeerID);
        sb.append(" | ");
        sb.append("until: ");
        sb.append(DateTimeHelper.long2ExactTimeString(this.until));
        sb.append(" | ");
        sb.append("newConnection: ");
        sb.append(this.newConnection);

        return sb.toString();
    }
}
