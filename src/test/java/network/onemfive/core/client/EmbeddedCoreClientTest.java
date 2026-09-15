package network.onemfive.core.client;

import network.onemfive.core.Core;
import network.onemfive.core.MockBusinessService;
import network.onemfive.core.identity.IdentityService;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import ra.common.network.Network;
import ra.did.nostr.Bip340;
import ra.did.nostr.NostrKeys;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Exercises {@link EmbeddedCoreClient} the way a host (Remnant's future
 * {@code :core-host}) would: boot, ask for identity, sign, register a transport,
 * send through it. Runs against the real in-process {@code Core} - no mocking of
 * the bus itself, only of the transport (see {@link TestProtocolHandle}).
 */
public class EmbeddedCoreClientTest {

    private EmbeddedCoreClient client;

    private static final class TestProtocolHandle implements ProtocolHandle {
        final ConcurrentLinkedQueue<Msg> sent = new ConcurrentLinkedQueue<>();

        @Override public String name() { return "TEST"; }
        @Override public Network network() { return Network.I2P; }
        @Override public TransportStatus status() { return new TransportStatus("TEST", true, "CONNECTED"); }
        @Override public boolean send(Msg msg) { sent.add(msg); return true; }
    }

    @Before
    public void setUp() {
        resetIdentityStorage(); // IdentityService persists at a fixed ~/.ra path, not a per-test tmp dir
        client = new EmbeddedCoreClient();
        Map<String, String> config = new HashMap<>();
        config.put(IdentityService.PROP_PASS, "test-pass-" + System.nanoTime());
        Assert.assertTrue("core should start", client.start(config));
    }

    /** Mirrors {@code IdentityServiceTest}'s cleanup - see that class for why. */
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

    @After
    public void tearDown() {
        client.stop();
    }

    @Test
    public void identityStatusReflectsARealNodeIdentity() {
        IdentityStatus id = client.identityStatus();
        Assert.assertNotNull(id.getPublicKeyHex());
        Assert.assertTrue(id.getDid().startsWith("did:nostr:"));
        Assert.assertTrue(id.hasSecret());
        Assert.assertTrue(id.isEncryptedAtRest());
    }

    @Test
    public void signAsNodeProducesAVerifiableSignature() throws Exception {
        byte[] canonical = "hello from CoreClient".getBytes(StandardCharsets.UTF_8);
        byte[] sig = client.signAsNode(canonical);
        Assert.assertEquals(64, sig.length);

        byte[] digest = MessageDigest.getInstance("SHA-256").digest(canonical);
        String pub = client.identityStatus().getPublicKeyHex();
        Assert.assertTrue(Bip340.verify(sig, digest, NostrKeys.fromHex(pub)));
    }

    @Test
    public void registeredProtocolIsDiscoveredAndReadyTransportsReportsIt() {
        TestProtocolHandle handle = new TestProtocolHandle();
        CoreInbound inbound = client.registerProtocol(handle);
        Assert.assertNotNull(inbound);

        Assert.assertTrue(client.awaitReady(5_000, "TEST"));

        List<TransportStatus> statuses = client.readyTransports();
        boolean found = false;
        for (TransportStatus s : statuses) {
            if ("TEST".equals(s.getName())) {
                found = true;
                Assert.assertTrue(s.isReady());
            }
        }
        Assert.assertTrue("TEST channel should be reported ready", found);
    }

    @Test
    public void sendReachesTheRegisteredProtocolHandle() throws Exception {
        TestProtocolHandle handle = new TestProtocolHandle();
        client.registerProtocol(handle);
        Assert.assertTrue(client.awaitReady(5_000, "TEST"));

        CountDownLatch replied = new CountDownLatch(1);
        Msg msg = new Msg().to("TEST").setPayload("payload".getBytes(StandardCharsets.UTF_8));
        client.send(msg, reply -> replied.countDown());

        Assert.assertTrue("reply callback should fire", replied.await(5, TimeUnit.SECONDS));
        Assert.assertEquals(1, handle.sent.size());
        Assert.assertArrayEquals("payload".getBytes(StandardCharsets.UTF_8), handle.sent.peek().getPayload());
    }

    /**
     * The channel names the service, {@code Msg.operation} names the method on it -
     * the business/data-channel case {@link #sendReachesTheRegisteredProtocolHandle}
     * doesn't cover, since a protocol channel only ever implements one operation.
     */
    @Test
    public void sendWithOperationInvokesTheNamedMethodOnABusinessChannel() throws Exception {
        MockBusinessService.clear();
        Core.get().registerService(MockBusinessService.class);
        Assert.assertTrue(Core.get().awaitServices(5_000, MockBusinessService.class));

        CountDownLatch replied = new CountDownLatch(1);
        Msg msg = new Msg()
                .to(MockBusinessService.class.getName(), "HANDLE_PAYMENT")
                .setPayload("pay bob 1000 sats".getBytes(StandardCharsets.UTF_8));
        client.send(msg, reply -> replied.countDown());

        Assert.assertTrue("reply callback should fire", replied.await(5, TimeUnit.SECONDS));
        Assert.assertEquals(1, MockBusinessService.RECEIVED.size());
        Assert.assertTrue(MockBusinessService.OPERATIONS.contains("HANDLE_PAYMENT"));
    }
}
