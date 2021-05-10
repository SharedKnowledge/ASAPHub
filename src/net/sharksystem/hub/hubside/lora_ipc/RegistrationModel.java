package net.sharksystem.hub.hubside.lora_ipc;

import javax.xml.bind.annotation.XmlElement;

/**
 * Model class to register/unregister a peer.
 */
public class RegistrationModel {

    private PeerModel peer;
    private boolean register;

    public RegistrationModel(PeerModel peer, boolean register){
        this.peer = peer;
        this.register = register;
    }

    public RegistrationModel(){}

    @XmlElement(name="peer")
    public PeerModel getPeer() {
        return peer;
    }

    public void setPeer(PeerModel peer) {
        this.peer = peer;
    }

    @XmlElement(name="register")
    public boolean isRegister() {
        return register;
    }

    public void setRegister(boolean register) {
        this.register = register;
    }
}
