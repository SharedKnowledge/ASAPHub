package net.sharksystem.hub;

import net.sharksystem.utils.Log;

import java.io.IOException;

class HubDataSession extends Thread {
    private final HubSession sideA;
    private final HubSession sideB;
    private final long maxIdleInMillis;

    HubDataSession(HubSession sideA, HubSession sideB, long maxIdleInMillis) {
        this.sideA = sideA;
        this.sideB = sideB;
        this.maxIdleInMillis = maxIdleInMillis;
    }

    public void run() {
        try {
            StreamPair streamPairA = sideA.createDataConnection(sideB.getPeerID(), this.maxIdleInMillis);
            StreamPair streamPairB = sideB.createDataConnection(sideB.getPeerID(), this.maxIdleInMillis);

            String a2bIDString = "HubSession(" + sideA.getPeerID() + ") => HubSession(" + sideB.getPeerID() + ")";
            StreamLink a2bLink = new StreamLink(
                    streamPairA.getInputStream(), streamPairB.getOutputStream(),
                    this.maxIdleInMillis, true, a2bIDString);

            String b2aIDString = "HubSession(" + sideB.getPeerID() + ") => HubSession(" + sideA.getPeerID() + ")";
            StreamLink b2aLink = new StreamLink(
                    streamPairB.getInputStream(), streamPairA.getOutputStream(),
                    this.maxIdleInMillis, true, b2aIDString);

            Log.writeLog(this, "launch stream links: " + this);
            a2bLink.start();
            b2aLink.start();

            try { a2bLink.join(); } catch (InterruptedException e) { /* ignore */ }
            try { b2aLink.join(); } catch (InterruptedException e) { /* ignore */ }
            Log.writeLog(this, "stream link threads ended: " + this);

            // end thread
            this.sideA.dataSessionEnded(streamPairA);
            this.sideB.dataSessionEnded(streamPairB);

        } catch (IOException e) {
            Log.writeLogErr(this, "cannot send channel clear pdu: " + e.getLocalizedMessage());
        }

        Log.writeLog(this, "finished: " + this);
    }

    public String toString() {
        return this.sideA.getPeerID() + " <=> " + this.sideB.getPeerID();
    }
}
