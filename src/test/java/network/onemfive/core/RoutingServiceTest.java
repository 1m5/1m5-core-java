package network.onemfive.core;

import network.onemfive.core.routing.RoutingService;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import ra.common.Envelope;

import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * The router as a bus service: given an envelope addressed to
 * {@link RoutingService}, it inspects ManCon + the connected
 * {@link MockProtocolService} (discovered through the bus) and pushes the transport
 * hop onto the slip, which the SEDA engine then carries.
 */
public class RoutingServiceTest {

    @Before
    public void setUp() {
        MockProtocolService.clear();
        Assert.assertTrue(Core.get().start(new Properties()));
        Core.get().registerServices(RoutingService.class, MockProtocolService.class);
        Assert.assertTrue("services should reach running",
                Core.get().awaitServices(5000, RoutingService.class, MockProtocolService.class));
        Assert.assertFalse("protocol adapter should be ready", Core.get().readyProtocols().isEmpty());
    }

    @After
    public void tearDown() {
        Core.reset();
    }

    @Test
    public void routerSelectsAConnectedProtocolAndDelegates() throws Exception {
        CountDownLatch replied = new CountDownLatch(1);
        Envelope e = Envelope.documentFactory();
        e.setSensitivity(4); // HIGH
        e.addRoute(RoutingService.class, RoutingService.OPERATION_ROUTE);
        e.ratchet();

        Core.get().send(e, envelope -> replied.countDown());

        Assert.assertTrue("slip should complete", replied.await(5, TimeUnit.SECONDS));
        Assert.assertTrue("router should have routed the envelope through the protocol adapter",
                MockProtocolService.SENT.contains(e.getId()));
    }
}
