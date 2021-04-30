package net.sharksystem.hub.hubside.lora_ipc;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

abstract class ConnectRequest {

    protected String sourcePeerID;
    protected String targetPeerID;

    public ConnectRequest(String sourcePeerID, String targetPeerID){
        this.sourcePeerID = sourcePeerID;
        this.targetPeerID = targetPeerID;
    }

    public ConnectRequest(){}

    @XmlElement(name="source_peer_id")
    public String getSourcePeerID() {
        return sourcePeerID;
    }

    public void setSourcePeerID(String sourcePeerID) {
        this.sourcePeerID = sourcePeerID;
    }

    @XmlElement(name="target_peer_id")
    public String getTargetPeerID() {
        return targetPeerID;
    }

    public void setTargetPeerID(String targetPeerID) {
        this.targetPeerID = targetPeerID;
    }
}
