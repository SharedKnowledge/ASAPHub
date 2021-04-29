package net.sharksystem.hub.hubside;

import java.io.IOException;
import java.io.InputStream;

public class IPCInputStream extends InputStream {
    private final CharSequence sourcePeerID;
    private final CharSequence targetPeerID;

    public IPCInputStream(CharSequence sourcePeerID, CharSequence targetPeerID) {
        this.sourcePeerID = sourcePeerID;
        this.targetPeerID = targetPeerID;
    }

    @Override
    public int read() throws IOException {
        return 0;
    }
}
