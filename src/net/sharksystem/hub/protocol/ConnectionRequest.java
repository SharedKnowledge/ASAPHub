package net.sharksystem.hub.protocol;

import net.sharksystem.asap.utils.DateTimeHelper;

public class ConnectionRequest {
    public final CharSequence sourcePeerID;
    public final CharSequence targetPeerID;
    public final long until;

    public ConnectionRequest(CharSequence sourcePeerID, CharSequence targetPeerID, long until) {
        this.sourcePeerID = sourcePeerID;
        this.targetPeerID = targetPeerID;
        this.until = until;
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

        return sb.toString();
    }
}
