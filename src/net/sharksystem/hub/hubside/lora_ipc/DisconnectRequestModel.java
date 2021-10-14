package net.sharksystem.hub.hubside.lora_ipc;

/**
 * Model class for disconnect request, which is used for IPC
 */
public class DisconnectRequestModel extends RequestModel{
    final static String IPCMessageType = "DisconnectRequest";

    public DisconnectRequestModel(String sourcePeerID, String targetPeerID) {
        super(sourcePeerID, targetPeerID);
    }

    @Override
    public String getIPCMessage() {
        return IPCModel.generateIPCMessage(new String[]{IPCMessageType, this.sourcePeerID, this.targetPeerID});
    }
}
