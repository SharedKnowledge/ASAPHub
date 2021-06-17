package net.sharksystem.hub.hubside.lora_ipc;

/**
 * Model class for connect request, which can be used to create a XML object for IPC.
 */
public class ConnectRequestModel extends RequestModel {

    final static String IPCMessageType = "ConnectRequest";

    private final int timeout;

    public ConnectRequestModel(String sourcePeerID, String targetPeerID, int timeout) {
        super(sourcePeerID, targetPeerID);
        this.timeout = timeout;
    }

    public int getTimeout() {
        return timeout;
    }

    @Override
    public String getIPCMessage() {
        return IPCModel.generateIPCMessage(new String[]{IPCMessageType, this.sourcePeerID, this.targetPeerID,
                Integer.toString(this.timeout)});
    }

}
