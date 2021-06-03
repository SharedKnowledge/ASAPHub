package net.sharksystem.hub.hubside.lora_ipc;

/**
 * Model class for connect request, which can be used to create a XML object for IPC.
 */
public class ConnectRequestModel extends IPCModel {

    final static String IPCMessageType = "ConnectRequest";

    private final String sourcePeerID;
    private final String targetPeerID;
    private final int timeout;

    public ConnectRequestModel(String sourcePeerID, String targetPeerID, int timeout) {
        this.sourcePeerID = sourcePeerID;
        this.targetPeerID = targetPeerID;
        this.timeout = timeout;
    }

    public String getSourcePeerID() {
        return sourcePeerID;
    }

    public String getTargetPeerID() {
        return targetPeerID;
    }

    public int getTimeout() {
        return timeout;
    }


    @Override
    public String getIPCMessage() {
        return IPCModel.generateIPCMessage(new String[]{IPCMessageType, this.sourcePeerID, this.targetPeerID,
                Integer.toString(this.timeout)});
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }
        if (!(o instanceof ConnectRequestModel)) {
            return false;
        }
        ConnectRequestModel connectRequestModelToCompare = (ConnectRequestModel) o;

        return connectRequestModelToCompare.getSourcePeerID().equals(this.sourcePeerID) &&
                connectRequestModelToCompare.getTargetPeerID().equals(this.targetPeerID) &&
                connectRequestModelToCompare.getTimeout() == this.timeout;
    }
}
