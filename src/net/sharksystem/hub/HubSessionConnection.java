package net.sharksystem.hub;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public interface HubSessionConnection {
    /** InputStream to read data from the other side */
    InputStream getInputStream();
    /** OutputStream to send data to the other side */
    OutputStream getOutputStream();
    /** Name of the peer on the other side */
    CharSequence getPeerID();

    /** close connection to peer */
    void close() throws IOException;

}
