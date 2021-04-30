package net.sharksystem.hub.hubside.lora_ipc;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name="connection_request")
public class ConnectRequestReplyModel extends ConnectRequest {

    private boolean connectionAvailable;

    @XmlElement(name="connection_available")
    public boolean isConnectionAvailable() {
        return connectionAvailable;
    }

    public void setConnectionAvailable(boolean connectionAvailable) {
        this.connectionAvailable = connectionAvailable;
    }
}
