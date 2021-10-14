package net.sharksystem.hub.hubside.lora_ipc;

import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

/**
 * parent class of all model classes
 */
public abstract class IPCModel {

    static final String IPCDelimiter = ",";

    /**
     * creates a String containing all items of passed Array delimited by |
     * @param args String Array
     * @return all items of Array as String delimited by |
     */
    public static String generateIPCMessage(String[] args) {
        StringBuilder message_str = new StringBuilder();
        for (int i = 0; i < args.length; i++) {
            message_str.append(args[i]);
            if (i != args.length - 1)
                message_str.append(IPCDelimiter);
        }
        return message_str.toString();
    }

    /**
     * creates appropriate model object from received IPC message
     * @param ipcMessage IPC message as String
     * @return object of class IPCModel with attributes from received message
     */
    public static IPCModel createModelObjectFromIPCString(String ipcMessage) {
        List<String> messageValues = new ArrayList<>();
        StringTokenizer stringTokenizer = new StringTokenizer(ipcMessage, IPCDelimiter);
        while (stringTokenizer.hasMoreTokens()) {
            messageValues.add(stringTokenizer.nextToken());
        }
        String messageType = messageValues.get(0);
        switch (messageType){
            case ConnectRequestModel.IPCMessageType:
                return new ConnectRequestModel(messageValues.get(1), messageValues.get(2),
                        Integer.parseInt(messageValues.get(3)));
            case DisconnectRequestModel.IPCMessageType:
                return new DisconnectRequestModel(messageValues.get(1), messageValues.get(2));
            case RegistrationModel.IPCMessageType:
                String registerStr = messageValues.get(2);
                return new RegistrationModel(messageValues.get(1), registerStr.equalsIgnoreCase("true"));
            case RegisteredPeersModel.IPCMessageType:
                List<String> registeredPeers = new ArrayList<>();
                for(int i = 1; i< messageValues.size(); i++){
                    registeredPeers.add(messageValues.get(i));
                }
                return new RegisteredPeersModel(registeredPeers);
        }
        return null;
    }

    /**
     * creates IPC message of model object
     * @return IPC message as String
     */
    public abstract String getIPCMessage();
}
