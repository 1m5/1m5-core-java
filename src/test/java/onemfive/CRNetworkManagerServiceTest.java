package onemfive;

import onemfive.routing.CRNetworkManagerService;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.Properties;
import java.util.logging.Logger;

public class CRNetworkManagerServiceTest {

    private static final Logger LOG = Logger.getLogger(CRNetworkManagerServiceTest.class.getName());

    private static CRNetworkManagerService service;
    private static MockProducer producer;
    private static Properties props;
    private static boolean ready = false;

    @BeforeClass
    public static void init() {
        LOG.info("Init...");
        props = new Properties();

        producer = new MockProducer();
        service = new CRNetworkManagerService(producer, null);

        ready = service.start(props);
    }

    @AfterClass
    public static void tearDown() {
        LOG.info("Teardown...");
        service.gracefulShutdown();
    }

    @Test
    public void verifyInitializedTest() {
        Assert.assertTrue(ready);
    }
}
