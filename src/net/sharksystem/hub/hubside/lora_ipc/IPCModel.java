package net.sharksystem.hub.hubside.lora_ipc;

import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

public abstract class IPCModel {

    static final String IPCDelimiter = ",";

    public static String generateIPCMessage(String[] args) {
        StringBuilder message_str = new StringBuilder();
        for (int i = 0; i < args.length; i++) {
            message_str.append(args[i]);
            if (i != args.length - 1)
                message_str.append(IPCDelimiter);
        }
        return message_str.toString();
    }

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
            case RegistrationModel.IPCMessageType:
                String registerStr = messageValues.get(2);
                return new RegistrationModel(messageValues.get(1), registerStr.equalsIgnoreCase("true"));
        }
        return null;
    }

    public abstract String getIPCMessage();
}
