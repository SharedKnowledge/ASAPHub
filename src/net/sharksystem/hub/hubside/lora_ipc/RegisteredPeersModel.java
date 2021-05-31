package net.sharksystem.hub.hubside.lora_ipc;

import java.util.List;

/**
 * Model class to exchange a list of registered peers.
 */
public class RegisteredPeersModel extends IPCModel {

    final static String IPCMessageType = "RegisteredPeers";

    private final List<String> registeredPeers;

    public RegisteredPeersModel(List<String> registeredPeers){
        this.registeredPeers = registeredPeers;
    }

    public List<String> getRegisteredPeers() {
        return registeredPeers;
    }

    @Override
    public String getIPCMessage() {
        String[] args = new String[this.registeredPeers.size() +1];
        args[0] = IPCMessageType;
        for(int i=0; i< this.registeredPeers.size(); i++){
            args[i+1] = this.registeredPeers.get(i);
        }
        return IPCModel.generateIPCMessage(args);
    }
}
