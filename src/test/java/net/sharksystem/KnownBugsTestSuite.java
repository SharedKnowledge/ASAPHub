package net.sharksystem;

import net.sharksystem.hub.HubConnectionManagerImpl;
import net.sharksystem.hub.HubConnectionManagerTest;
import net.sharksystem.hub.KnownBugsHubUsageTests;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;

import java.io.IOException;

@RunWith(Suite.class)
@Suite.SuiteClasses({
        KnownBugsHubUsageTests.class,
})

public class KnownBugsTestSuite {
    private HubConnectionManagerTest hubConnectionManagerTest;
    @Before
    public void setUp(){
        hubConnectionManagerTest = new HubConnectionManagerTest();
    }

    @Test
    public void connectHubGoodMultipleTwoAttempts() throws SharkException, IOException, InterruptedException {
        hubConnectionManagerTest.connectHubGoodMultipleTwoAttempts();
    }
}
