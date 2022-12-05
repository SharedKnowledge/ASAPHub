package net.sharksystem.hub.hubside.lora_ipc;

/**
 * parent class for model classes of ConnectRequestModel and DisconnectRequestModel
 */
public abstract class RequestModel extends IPCModel {

    final String sourcePeerID;
    final String targetPeerID;

    public RequestModel(String sourcePeerID, String targetPeerID) {
        this.sourcePeerID = sourcePeerID;
        this.targetPeerID = targetPeerID;
    }

    /**
     * getter for source peer id
     * @return peer id as String
     */
    public String getSourcePeerID() {
        return sourcePeerID;
    }

    /**
     * getter for target peer id
     * @return peer id as String
     */
    public String getTargetPeerID() {
        return targetPeerID;
    }

}
