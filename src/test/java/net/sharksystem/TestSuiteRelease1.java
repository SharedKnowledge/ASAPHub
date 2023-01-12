package net.sharksystem;

import net.sharksystem.hub.hubside.lora_ipc.IPCModelTest;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import net.sharksystem.hub.HubUsageTests;

@RunWith(Suite.class)
@Suite.SuiteClasses({
        HubUsageTests.class,
        IPCModelTest.class
})

public class TestSuiteRelease1 {
}
