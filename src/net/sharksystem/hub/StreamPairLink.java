package net.sharksystem.hub;

import java.io.IOException;

public class StreamPairLink {
    private StreamLink streamLinkA2B;
    private StreamLink streamLinkB2A;

    public StreamPairLink(StreamPair pairA, CharSequence idA, StreamPair pairB, CharSequence idB) throws IOException {
        String tagA2B = idA + " -> " + idB;
        this.streamLinkA2B = new StreamLink(pairA.getInputStream(), pairB.getOutputStream(), true, tagA2B);
        String tagB2A = idA + " <- " + idB;
        this.streamLinkB2A = new StreamLink(pairB.getInputStream(), pairA.getOutputStream(), true, tagB2A);

        this.streamLinkA2B.start();
        this.streamLinkB2A.start();

    }
}
