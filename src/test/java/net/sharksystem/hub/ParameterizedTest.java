package net.sharksystem.hub;

import net.sharksystem.asap.ASAPException;
import net.sharksystem.hub.hubside.ASAPTCPHub;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static org.junit.Assert.fail;

@RunWith(Parameterized.class)
public class ParameterizedTest {

    @Parameterized.Parameters
    public static Collection<Object[]> getFiles() {
        Collection<Object[]> params = new ArrayList<Object[]>();
        for (File testPlanSubDirs : new File("testplans").listFiles()) {
            Object[] subDir = new Object[]{testPlanSubDirs};
            params.add(subDir);
        }
        return params;
    }

    private File testPlanDir;
    private List<Runnable> connectorCLIList;
    private final int port = 6910;
    private final String host = "localhost";

    public ParameterizedTest(File testPlanDir) {
        this.testPlanDir = testPlanDir;
        connectorCLIList = new ArrayList<>();
    }

    @Test
    public void testY() throws IOException, ASAPException, InterruptedException {
        ASAPTCPHub hub = new ASAPTCPHub(port, true);
        hub.setPortRange(7000, 9000); // optional - required to configure a firewall
        hub.setMaxIdleConnectionInSeconds(60);
        new Thread(hub).start();
        File[] testPlans =testPlanDir.listFiles();
        for (int i = 0; i<testPlans.length-1; i++) {
            FileInputStream fis = new FileInputStream(testPlans[i]);
            Runnable r = new Runnable() {
                @Override
                public void run() {
                    try {
                        new HubConnectorCLI(fis, System.out, host, port, true).startCLI();
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    } catch (ASAPHubException e) {
                        throw new RuntimeException(e);
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }
            };

            File lastTestPlanElem =testPlans[testPlans.length-1];
            FileInputStream fis2 = new FileInputStream(lastTestPlanElem);
            new Thread(r).start();

            new HubConnectorCLI(fis2, System.out, host, port, true).startCLI();

            Thread.sleep(4000);
        }
    }

}
