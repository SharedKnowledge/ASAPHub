package net.sharksystem.hub.peerside;

import net.sharksystem.hub.ASAPHubException;

import java.io.IOException;

public interface HubConnectorDescription {
    byte TCP = 0x00;

    /**
     * provides type of this hub description. Known type constants are defined with this interface.
     * @return
     */
    byte getType();

    /**
     * Provides host name on which hub is running
     * @throws ASAPHubException if no such hostname exists in used protocol.
     */
    CharSequence getHostName() throws ASAPHubException;

    /**
     * Provides port number on hub side
     * @throws ASAPHubException if no such concept as port exists in used protocol.
     */
    int getPortNumber() throws ASAPHubException;

    /**
     * Is it allowed to create a new connection / channel for a peer encounter?
     * @return
     */
    boolean canMultiChannel();

    boolean isSame(HubConnectorDescription otherHcd);

    byte[] serialize() throws IOException;
}
