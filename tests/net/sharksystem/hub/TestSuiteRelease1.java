package net.sharksystem.hub;

import net.sharksystem.hub.hubside.lora_ipc.IPCModelTest;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;

@RunWith(Suite.class)
@Suite.SuiteClasses({
        HubUsageTests.class,
        IPCModelTest.class
})

public class TestSuiteRelease1 {
}
