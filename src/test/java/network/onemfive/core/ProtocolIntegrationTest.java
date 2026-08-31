package network.onemfive.core;

import network.onemfive.core.protocol.NetworkServiceProtocol;
import network.onemfive.core.routing.RoutingService;
import network.onemfive.core.service.ProtocolService;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import ra.common.Envelope;

import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * A {@code ra.common.network.NetworkService} (what {@code i2p-java}'s
 * {@code I2PService} is) wrapped by {@link NetworkServiceProtocol} is discovered by
 * the router as a {@link ProtocolService} and receives the routed hop. This is the
 * same path {@code I2PProtocolService} takes, without a real I2P router.
 */
public class ProtocolIntegrationTest {

    @Before
    public void setUp() {
        MockNetworkService.clear();
        Assert.assertTrue(Core.get().start(new Properties()));
        Core.get().registerServices(RoutingService.class, MockNetworkServiceProtocol.class);
        Assert.assertTrue(Core.get().awaitServices(5000,
                RoutingService.class, MockNetworkServiceProtocol.class));
    }

    @After
    public void tearDown() {
        Core.reset();
    }

    @Test
    public void networkServiceAdapterIsDiscoveredAndRoutedTo() throws Exception {
        // The wrapped NetworkService reports CONNECTED -> the adapter is "ready".
        Assert.assertEquals(1, Core.get().readyProtocols().size());
        ProtocolService p = Core.get().readyProtocols().get(0);
        Assert.assertTrue(p instanceof MockNetworkServiceProtocol);
        Assert.assertEquals(ra.common.network.Network.I2P, p.getNetwork());

        CountDownLatch replied = new CountDownLatch(1);
        Envelope e = Envelope.documentFactory();
        e.setSensitivity(4); // HIGH
        e.addRoute(RoutingService.class, RoutingService.OPERATION_ROUTE);
        e.ratchet();
        Core.get().send(e, envelope -> replied.countDown());

        Assert.assertTrue("slip should complete", replied.await(5, TimeUnit.SECONDS));
        Assert.assertTrue("the wrapped NetworkService should have been asked to send",
                MockNetworkService.SENT_OUT.contains(e.getId()));
    }
}
