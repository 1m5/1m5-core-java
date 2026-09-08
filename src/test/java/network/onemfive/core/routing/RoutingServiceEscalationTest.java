package network.onemfive.core.routing;

import network.onemfive.core.Core;
import network.onemfive.core.ManCon;
import network.onemfive.core.ManConStatus;
import network.onemfive.core.ToggleProtocolService;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import ra.common.Envelope;
import ra.common.network.Network;
import ra.common.network.NetworkPeer;

import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The router on the bus: {@code maxAvailable} tracking + listener, hold + retry
 * with backoff, dead-letter, and direct dispatch once a path appears - driven
 * synchronously through a {@link RetryScheduler.Manual}.
 */
public class RoutingServiceEscalationTest {

    private RoutingService rs;
    private RetryScheduler.Manual retries;

    @Before
    public void setUp() {
        ToggleProtocolService.reset(Network.I2P, false); // registered, not ready yet
        Assert.assertTrue(Core.get().start(new Properties()));
        Core.get().registerServices(RoutingService.class, ToggleProtocolService.class);
        Assert.assertTrue(Core.get().awaitServices(5000,
                RoutingService.class, ToggleProtocolService.class));

        rs = Core.get().bus().getService(RoutingService.class);
        retries = new RetryScheduler.Manual();
        rs.setRetryScheduler(retries);
        rs.setRetryStrategy(RetryStrategy.of(10, 10, 2, 100, 60_000));
    }

    @After
    public void tearDown() {
        Core.reset();
    }

    private static Envelope routed(int sensitivity) {
        Envelope e = Envelope.documentFactory();
        e.setSensitivity(sensitivity);
        e.addRoute(RoutingService.class, RoutingService.OPERATION_ROUTE);
        e.ratchet();
        return e;
    }

    private void send(Envelope e) {
        Core.get().send(e);
        sleep(150); // let the bus worker run route()
    }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) { }
    }

    @Test
    public void maxAvailableTracksReadinessAndFiresListener() {
        AtomicInteger fires = new AtomicInteger();
        Core.get().addManConStatusListener(fires::incrementAndGet);

        rs.refreshAvailability();
        Assert.assertEquals(ManCon.NONE, Core.get().manConStatus().getMaxAvailable());

        ToggleProtocolService.setReady(true);
        rs.refreshAvailability();
        Assert.assertEquals(ManCon.VERYHIGH, Core.get().manConStatus().getMaxAvailable());
        Assert.assertTrue("listener fired on change", fires.get() >= 1);

        int before = fires.get();
        rs.refreshAvailability(); // no change
        Assert.assertEquals(before, fires.get());
    }

    @Test
    public void holdsWhenNoPathThenRoutesOnceTransportIsReady() {
        Envelope e = routed(4); // HIGH
        send(e);

        Assert.assertTrue("held - I2P not ready", rs.isHeld(e.getId()));
        Assert.assertTrue(retries.isPending(e.getId()));
        Assert.assertTrue(ToggleProtocolService.SENT.isEmpty());

        ToggleProtocolService.setReady(true);
        retries.runDue();           // fires reroute -> Core.send(e)
        sleep(150);

        Assert.assertFalse("no longer held", rs.isHeld(e.getId()));
        Assert.assertTrue("routed through the now-ready transport",
                ToggleProtocolService.SENT.contains(e.getId()));
    }

    @Test
    public void deadLettersAfterTheRetryWindowIsExhausted() {
        rs.setRetryStrategy(RetryStrategy.of(5, 5, 2, 20, 15)); // give up after 15ms held
        Envelope e = routed(4);
        send(e);
        Assert.assertTrue(rs.isHeld(e.getId()));

        sleep(30);
        retries.runDue();           // reroute -> still no path -> giveUp -> DROP
        sleep(50);

        Assert.assertFalse("dropped from the hold queue", rs.isHeld(e.getId()));
        Assert.assertFalse("retry cancelled", retries.isPending(e.getId()));
        Assert.assertFalse("dead-letter error recorded",
                Envelope.getErrorMessages(e).isEmpty());
    }

    @Test
    public void directDispatchToAReadyAddressMatchedPeer() {
        ToggleProtocolService.reset(Network.I2P, true);
        rs.refreshAvailability();

        NetworkPeer bob = new NetworkPeer(Network.I2P);
        bob.setId("bob");
        rs.peerDirectory().put(bob);

        Envelope e = Envelope.documentFactory();
        e.setSensitivity(4);
        e.setHeader(RoutingService.HEADER_DEST_FINGERPRINT, "bob");
        e.addRoute(RoutingService.class, RoutingService.OPERATION_ROUTE);
        e.ratchet();
        send(e);

        Assert.assertFalse(rs.isHeld(e.getId()));
        Assert.assertTrue(ToggleProtocolService.SENT.contains(e.getId()));
    }
}
