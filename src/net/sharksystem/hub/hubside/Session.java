package net.sharksystem.hub.hubside;

import java.io.InputStream;
import java.io.OutputStream;

public interface Session {
    void setInputStream();
    void setOutputStream();
    OutputStream getOutputStream();
    InputStream getInputStream();
}
