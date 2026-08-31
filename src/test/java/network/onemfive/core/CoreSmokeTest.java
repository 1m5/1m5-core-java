package network.onemfive.core;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import ra.common.Client;
import ra.common.Envelope;

import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * The whole stack in one shot: {@code Core} -> {@code ServiceBus} -> {@code SEDABus}
 * carrying an {@link Envelope} through business services by walking its
 * {@link ra.common.route.DynamicRoutingSlip}.
 */
public class CoreSmokeTest {

    @Before
    public void setUp() {
        MockBusinessService.clear();
        MockRelayService.clear();
        Properties p = new Properties();
        Assert.assertTrue("Core should start", Core.get().start(p));
        Core.get().registerServices(MockBusinessService.class, MockRelayService.class);
        Assert.assertTrue(Core.get().awaitServices(5000,
                MockBusinessService.class, MockRelayService.class));
    }

    @After
    public void tearDown() {
        Core.reset();
    }

    @Test
    public void deliversToServiceAndFiresCallback() throws Exception {
        CountDownLatch replied = new CountDownLatch(1);
        Envelope e = Envelope.documentFactory();
        e.addRoute(MockBusinessService.class, "HANDLE");
        e.ratchet();

        Core.get().send(e, new Client() {
            @Override
            public void reply(Envelope envelope) {
                replied.countDown();
            }
        });

        Assert.assertTrue("producer callback should fire at end of slip",
                replied.await(5, TimeUnit.SECONDS));
        Assert.assertTrue("service should have received the envelope",
                MockBusinessService.RECEIVED.contains(e.getId()));
        Assert.assertTrue(MockBusinessService.OPERATIONS.contains("HANDLE"));
    }

    @Test
    public void slipWalksMultipleHopsInOrder() throws Exception {
        CountDownLatch replied = new CountDownLatch(1);
        Envelope e = Envelope.documentFactory();
        // LIFO stack: push the later hop first so execution order is A then B.
        e.addRoute(MockRelayService.class, "HOP_B");
        e.addRoute(MockBusinessService.class, "HOP_A");
        e.ratchet();

        Core.get().send(e, envelope -> replied.countDown());

        Assert.assertTrue(replied.await(5, TimeUnit.SECONDS));
        Assert.assertTrue("first hop", MockBusinessService.RECEIVED.contains(e.getId()));
        Assert.assertTrue("second hop", MockRelayService.RECEIVED.contains(e.getId()));
        Assert.assertTrue(MockBusinessService.OPERATIONS.contains("HOP_A"));
    }
}
