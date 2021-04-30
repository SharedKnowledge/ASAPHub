package net.sharksystem.hub.hubside.lora_ipc;

import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name="connection_request")
public class ConnectRequestModel extends ConnectRequest {

    private int timeout;

    public ConnectRequestModel(String sourcePeerID, String targetPeerID, int timeout){
        this.sourcePeerID = sourcePeerID;
        this.targetPeerID = targetPeerID;
        this.timeout = timeout;
    }

    public ConnectRequestModel(){}

    public int getTimeout() {
        return timeout;
    }

    public void setTimeout(int timeout) {
        this.timeout = timeout;
    }
}
