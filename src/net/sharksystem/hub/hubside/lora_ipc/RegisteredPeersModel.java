package net.sharksystem.hub.hubside.lora_ipc;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.List;

@XmlRootElement(name="registered_peers")
public class RegisteredPeersModel {

    @XmlElement(name="peer")
    public List<PeerModel> getRegisteredPeers() {
        return registeredPeers;
    }

    public void setRegisteredPeers(List<PeerModel> registeredPeers) {
        this.registeredPeers = registeredPeers;
    }

    private List<PeerModel> registeredPeers;


}
