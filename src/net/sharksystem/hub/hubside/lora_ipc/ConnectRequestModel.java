package net.sharksystem.hub.hubside.lora_ipc;

/**
 * Model class for connect request, which is used for IPC
 */
public class ConnectRequestModel extends RequestModel {

    final static String IPCMessageType = "ConnectRequest";

    private final int timeout;

    public ConnectRequestModel(String sourcePeerID, String targetPeerID, int timeout) {
        super(sourcePeerID, targetPeerID);
        this.timeout = timeout;
    }

    /**
     * getter for connect request timeout value
     * @return timeout as int
     */
    public int getTimeout() {
        return timeout;
    }

    @Override
    public String getIPCMessage() {
        return IPCModel.generateIPCMessage(new String[]{IPCMessageType, this.sourcePeerID, this.targetPeerID,
                Integer.toString(this.timeout)});
    }

}
