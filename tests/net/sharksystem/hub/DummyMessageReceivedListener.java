package net.sharksystem.hub;

import net.sharksystem.asap.ASAPHop;
import net.sharksystem.asap.ASAPMessageReceivedListener;
import net.sharksystem.asap.ASAPMessages;

import java.io.IOException;
import java.util.Iterator;
import java.util.List;

public class DummyMessageReceivedListener implements ASAPMessageReceivedListener {
    private final String peerName;
    public int counter = 0;

    DummyMessageReceivedListener(String peerName) {
        this.peerName = peerName;
    }

    DummyMessageReceivedListener() {
        this(null);
    }

    @Override
    public void asapMessagesReceived(ASAPMessages messages,
                                     String senderE2E, // E2E part
                                     List<ASAPHop> asapHop) throws IOException {

        this.counter++;

        CharSequence format = messages.getFormat();
        CharSequence uri = messages.getURI();
        if (peerName != null) {
            System.out.print(peerName);
        }

        System.out.println("asap message received (" + format + " | " + uri + "). size == " + messages.size());
        Iterator<byte[]> yourPDUIter = messages.getMessages();

        System.out.println("message: " + new String(yourPDUIter.next()));
    }
}

