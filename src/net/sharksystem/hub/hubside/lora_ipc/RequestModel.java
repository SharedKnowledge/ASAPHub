package net.sharksystem.hub.hubside.lora_ipc;

public abstract class RequestModel extends IPCModel {

    final String sourcePeerID;
    final String targetPeerID;

    public RequestModel(String sourcePeerID, String targetPeerID) {
        this.sourcePeerID = sourcePeerID;
        this.targetPeerID = targetPeerID;
    }

    public String getSourcePeerID() {
        return sourcePeerID;
    }

    public String getTargetPeerID() {
        return targetPeerID;
    }

}
