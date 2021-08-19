package net.sharksystem.hub;

import net.sharksystem.streams.StreamPair;

public interface ConnectionPreparer {
    byte readyByte = 42; // what else would it be
    void prepare(StreamPair streamPair);
}
