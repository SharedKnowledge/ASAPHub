package net.sharksystem.hub;

import net.sharksystem.asap.ASAPException;
import net.sharksystem.hub.hubside.ASAPTCPHub;
import org.junit.Assert;
import org.junit.Test;

import java.beans.ExceptionListener;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

public class CLITest {

    private final int port = 6910;
    private final String host = "localhost";
    private final String testFileName = "test_plan.txt";
    private final String testFileName2 = "test_plan2.txt";


    @Test
    public void readFromFile() throws IOException, ASAPException, InterruptedException {
        File testplanFile = new File(testFileName);
        System.out.println(testplanFile.getAbsolutePath());
        FileInputStream fis = new FileInputStream(new File(testFileName));

        ASAPTCPHub hub = new ASAPTCPHub(port, true);
        hub.setPortRange(7000, 9000); // optional - required to configure a firewall
        hub.setMaxIdleConnectionInSeconds(60);
        new Thread(hub).start();
        HubConnectorCLILocal cli = new HubConnectorCLILocal(fis, System.out, host, port, true);

        cli.startCLI();

        Thread.sleep(5000);
    }

    @Test
    public void readFromFile2() throws IOException, ASAPException, InterruptedException {
        CLITestExceptionListener listener = new CLITestExceptionListener();

        File testplanFile = new File(testFileName);
        System.out.println(testplanFile.getAbsolutePath());
        FileInputStream fis = new FileInputStream(testFileName);
        FileInputStream fis2 = new FileInputStream(testFileName2);


//        ASAPTCPHub hub = new ASAPTCPHub(port, true);
//        hub.setPortRange(7000, 9000); // optional - required to configure a firewall
//        hub.setMaxIdleConnectionInSeconds(60);
//        new Thread(hub).start();
        HubConnectorCLI cli = new HubConnectorCLI(fis, System.out, host, port);
        HubConnectorCLI cli2 = new HubConnectorCLI(fis2, System.err, host, port);
        Runnable r = new Runnable() {
            @Override
            public void run() {
                try {
                    cli.startCLI();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                } catch (ASAPHubException e) {
                    throw new RuntimeException(e);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        };
        new Thread(r).start();

        cli2.startCLI();
        Thread.sleep(20000);

        Assert.assertTrue(listener.getException() == null);


    }

    private class CLITestExceptionListener implements ExceptionListener {
        private Exception exception = null;

        @Override
        public void exceptionThrown(Exception e) {
            exception = e;
        }

        public Exception getException(){
            return exception;
        }
    }

}
