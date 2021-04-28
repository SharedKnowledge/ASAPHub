package net.sharksystem.hub;

import java.io.IOException;

public class StreamPairLink {
    public StreamPairLink(StreamPair pairA, CharSequence idA, StreamPair pairB, CharSequence idB) throws IOException {
        StreamLink streamLinkA2B = new StreamLink(pairA.getInputStream(), pairB.getOutputStream(), false);
        StreamLink streamLinkB2A = new StreamLink(pairB.getInputStream(), pairA.getOutputStream(), false);

        streamLinkA2B.start();
        streamLinkB2A.start();
    }
}
