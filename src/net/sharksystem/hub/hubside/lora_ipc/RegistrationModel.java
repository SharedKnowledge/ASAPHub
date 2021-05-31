package net.sharksystem.hub.hubside.lora_ipc;

import javax.xml.bind.annotation.XmlElement;

/**
 * Model class to register/unregister a peer.
 */
public class RegistrationModel extends IPCModel {

    final static String IPCMessageType = "Registration";

    private String peerId;
    private boolean register;

    public RegistrationModel(String peerId, boolean register){
        this.peerId = peerId;
        this.register = register;
    }

    public RegistrationModel(){}

    public String getPeerId() {
        return peerId;
    }


    public boolean isRegister() {
        return register;
    }

    @Override
    public String getIPCMessage() {
        return IPCModel.generateIPCMessage(new String[]{IPCMessageType, this.peerId, String.valueOf(this.register)});
    }

}
