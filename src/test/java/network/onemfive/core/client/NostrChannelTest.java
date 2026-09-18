package network.onemfive.core.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import network.onemfive.core.Core;
import network.onemfive.core.business.NostrService;
import network.onemfive.core.identity.IdentityService;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import ra.did.nostr.NostrEvent;
import ra.did.nostr.NostrIdentity;
import ra.did.nostr.NostrKeyRing;
import ra.did.nostr.relay.NostrRelayOps;
import ra.did.nostr.relay.NostrRelayService;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Catches drift between {@link NostrChannel}'s app-safe constants and {@code
 * ra.did.nostr.relay.NostrRelayOps}'s real ones (mirrors {@code
 * BitcoinChannelTest}'s role for {@link BitcoinChannel}), and proves the
 * actual round trip a host depends on: a real relay's {@code OK}/{@code EOSE}
 * response really does reach {@link EmbeddedCoreClient#send(Msg,
 * ReplyHandler)}'s callback, against a real (fake, in-process) relay - not a
 * mocked {@code NostrRelayService}.
 */
public class NostrChannelTest {

    private static final int PORT = 18790;

    private FakeRelay relay;
    private EmbeddedCoreClient client;

    @Before
    public void setUp() throws InterruptedException {
        resetIdentityStorage();
        relay = new FakeRelay(PORT);
        relay.start();
        relay.awaitReady();

        client = new EmbeddedCoreClient();
        Map<String, String> config = new HashMap<>();
        config.put(IdentityService.PROP_PASS, "test-pass-" + System.nanoTime());
        config.put("1m5.nostr.enabled", "true");
        config.put(NostrRelayService.PROP_RELAYS, "ws://127.0.0.1:" + PORT);
        Assert.assertTrue("core should start", client.start(config));
        Assert.assertTrue("NostrService should reach RUNNING",
                Core.get().awaitServices(15_000, NostrService.class));
    }

    @After
    public void tearDown() throws InterruptedException {
        client.stop();
        relay.stop(1000);
    }

    private static void resetIdentityStorage() {
        IdentityService probe = new IdentityService();
        probe.start(new Properties());
        File dir = probe.getServiceDirectory();
        probe.shutdown();
        for (String n : new String[]{"node.pub", "node.sec"}) new File(dir, n).delete();
        File idDir = new File(dir, "identity");
        File[] sealed = idDir.listFiles();
        if (sealed != null) for (File f : sealed) f.delete();
    }

    private static NostrEvent signedNote(String content) {
        NostrKeyRing ring = new NostrKeyRing();
        Assert.assertTrue(ring.init(new Properties()));
        NostrIdentity id = ring.generateIdentity();
        NostrEvent ev = NostrEvent.unsigned(1, new ArrayList<>(), content, 1_700_000_000L);
        return ring.sign(ev, id);
    }

    @Test
    public void mirrorsNostrRelayOpsExactly() {
        Assert.assertEquals(NostrService.class.getName(), NostrChannel.NAME);
        Assert.assertEquals(NostrRelayOps.OPERATION_PUBLISH, NostrChannel.OPERATION_PUBLISH);
        Assert.assertEquals(NostrRelayOps.OPERATION_QUERY, NostrChannel.OPERATION_QUERY);
        Assert.assertEquals(NostrRelayOps.OPERATION_STATUS, NostrChannel.OPERATION_STATUS);
        Assert.assertEquals(NostrRelayOps.HEADER_EVENT_JSON, NostrChannel.HEADER_EVENT_JSON);
        Assert.assertEquals(NostrRelayOps.HEADER_RESULTS_JSON, NostrChannel.HEADER_RESULTS_JSON);
        Assert.assertEquals(NostrRelayOps.HEADER_FILTER_JSON, NostrChannel.HEADER_FILTER_JSON);
        Assert.assertEquals(NostrRelayOps.HEADER_TIMEOUT_MS, NostrChannel.HEADER_TIMEOUT_MS);
        Assert.assertEquals(NostrRelayOps.HEADER_EVENTS_JSON, NostrChannel.HEADER_EVENTS_JSON);
        Assert.assertEquals(NostrRelayOps.HEADER_CONNECTED_RELAYS, NostrChannel.HEADER_CONNECTED_RELAYS);
        Assert.assertEquals(NostrRelayOps.HEADER_TOTAL_RELAYS, NostrChannel.HEADER_TOTAL_RELAYS);
    }

    @Test
    public void statusReportsTheConnectedFakeRelay() throws Exception {
        CountDownLatch replied = new CountDownLatch(1);
        AtomicReference<Msg> replyRef = new AtomicReference<>();
        Msg msg = new Msg().to(NostrChannel.NAME, NostrChannel.OPERATION_STATUS);
        client.send(msg, reply -> { replyRef.set(reply); replied.countDown(); });

        Assert.assertTrue("reply callback should fire", replied.await(15, TimeUnit.SECONDS));
        Assert.assertEquals("1", replyRef.get().header(NostrChannel.HEADER_CONNECTED_RELAYS));
        Assert.assertEquals("1", replyRef.get().header(NostrChannel.HEADER_TOTAL_RELAYS));
    }

    @Test
    public void publishResponseReachesTheReplyCallback() throws Exception {
        NostrEvent event = signedNote("hello from EmbeddedCoreClient");
        CountDownLatch replied = new CountDownLatch(1);
        AtomicReference<Msg> replyRef = new AtomicReference<>();
        Msg msg = new Msg()
                .to(NostrChannel.NAME, NostrChannel.OPERATION_PUBLISH)
                .header(NostrChannel.HEADER_EVENT_JSON, event.toJson());
        client.send(msg, reply -> { replyRef.set(reply); replied.countDown(); });

        Assert.assertTrue("reply callback should fire", replied.await(15, TimeUnit.SECONDS));
        String resultsJson = replyRef.get().header(NostrChannel.HEADER_RESULTS_JSON);
        Assert.assertNotNull(resultsJson);
        JsonArray results = JsonParser.parseString(resultsJson).getAsJsonArray();
        Assert.assertEquals(1, results.size());
        Assert.assertTrue(results.get(0).getAsJsonObject().get("accepted").getAsBoolean());
    }
}