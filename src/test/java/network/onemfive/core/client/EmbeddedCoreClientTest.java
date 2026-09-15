package network.onemfive.core.client;

import network.onemfive.core.Core;
import network.onemfive.core.MockBusinessService;
import network.onemfive.core.identity.IdentityService;
import network.onemfive.core.routing.RoutingService;
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
        @Override public String localAddress() { return "test-local.b32.i2p"; }
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

    /** What powers "My Card": the app's own address on a transport, surfaced without touching {@code ProtocolService}/{@code NetworkPeer} directly. */
    @Test
    public void readyTransportsSurfacesTheHostsOwnLocalAddress() {
        TestProtocolHandle handle = new TestProtocolHandle();
        client.registerProtocol(handle);
        Assert.assertTrue(client.awaitReady(5_000, "TEST"));

        boolean found = false;
        for (TransportStatus s : client.readyTransports()) {
            if ("TEST".equals(s.getName())) {
                found = true;
                Assert.assertEquals("test-local.b32.i2p", s.getLocalAddress());
            }
        }
        Assert.assertTrue("TEST channel should be present", found);
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

    /** Closes the gap {@link CoreInbound}'s javadoc used to describe: a Msg sent to an app-registered channel now actually reaches its handler. */
    @Test
    public void registeredChannelReceivesAMessageAddressedToIt() throws Exception {
        CountDownLatch received = new CountDownLatch(1);
        ConcurrentLinkedQueue<Msg> inbox = new ConcurrentLinkedQueue<>();
        boolean ok = client.registerChannel("messaging", msg -> { inbox.add(msg); received.countDown(); });
        Assert.assertTrue("registerChannel should complete", ok);

        Msg msg = new Msg().to("messaging", "RECEIVE").setPayload("hi".getBytes(StandardCharsets.UTF_8));
        Assert.assertTrue(client.send(msg));

        Assert.assertTrue("handler should receive the message", received.await(5, TimeUnit.SECONDS));
        Assert.assertArrayEquals("hi".getBytes(StandardCharsets.UTF_8), inbox.peek().getPayload());
    }

    /**
     * The full inbound-from-transport path: what an {@code I2PProtocolAdapter}
     * (or any {@link ProtocolHandle}) does when a real peer's message arrives -
     * construct a {@link Msg} addressed at the app's business channel and hand it
     * to the {@link CoreInbound} {@link CoreClient#registerProtocol} returned.
     * Before {@code registerChannel} existed, this had nowhere to go.
     */
    @Test
    public void aProtocolHandlesInboundMessageReachesARegisteredChannel() throws Exception {
        TestProtocolHandle handle = new TestProtocolHandle();
        CoreInbound protocolInbound = client.registerProtocol(handle);
        Assert.assertTrue(client.awaitReady(5_000, "TEST"));

        CountDownLatch received = new CountDownLatch(1);
        ConcurrentLinkedQueue<Msg> inbox = new ConcurrentLinkedQueue<>();
        client.registerChannel("messaging", msg -> { inbox.add(msg); received.countDown(); });

        Msg incoming = new Msg().to("messaging", "RECEIVE").setSender("peer-1")
                .setPayload("hello from a peer".getBytes(StandardCharsets.UTF_8));
        protocolInbound.accept(incoming);

        Assert.assertTrue("handler should receive the relayed message", received.await(5, TimeUnit.SECONDS));
        Assert.assertEquals("peer-1", inbox.peek().getSender());
        Assert.assertArrayEquals("hello from a peer".getBytes(StandardCharsets.UTF_8), inbox.peek().getPayload());
    }

    /**
     * The real messaging outbound path: the app knows nothing about protocols,
     * only a contact's fingerprint - it addresses {@link RoutingService} and lets
     * it resolve the right transport from {@link network.onemfive.core.routing.PeerDirectory},
     * populated here by a prior {@code REGISTER_PEER} message (what a contact's
     * out-of-band address exchange would trigger). Exercises the fix to
     * {@code MsgTranslator.toEnvelope} that stopped {@code x.dest.*} headers from
     * being silently dropped when the addressed channel isn't a protocol one.
     */
    @Test
    public void sendToRoutingServiceWithOnlyAFingerprintReachesTheAddressMatchedProtocol() throws Exception {
        TestProtocolHandle handle = new TestProtocolHandle();
        client.registerProtocol(handle);
        Assert.assertTrue(client.awaitReady(5_000, "TEST"));

        CountDownLatch registered = new CountDownLatch(1);
        Msg register = new Msg()
                .to(RoutingService.class.getName(), RoutingService.OPERATION_REGISTER_PEER)
                .header("x.dest.peerId", "bob")
                .header("x.dest.i2p", "bob.b32.i2p");
        client.send(register, reply -> registered.countDown());
        Assert.assertTrue("peer registration should complete", registered.await(5, TimeUnit.SECONDS));

        CountDownLatch replied = new CountDownLatch(1);
        Msg msg = new Msg()
                .to(RoutingService.class.getName(), RoutingService.OPERATION_ROUTE)
                .header("x.sensitivity", "4")
                .header("x.dest.peerId", "bob")
                .setPayload("hi bob".getBytes(StandardCharsets.UTF_8));
        client.send(msg, reply -> replied.countDown());

        Assert.assertTrue("reply callback should fire", replied.await(5, TimeUnit.SECONDS));
        Assert.assertEquals(1, handle.sent.size());
        Assert.assertArrayEquals("hi bob".getBytes(StandardCharsets.UTF_8), handle.sent.peek().getPayload());
        Assert.assertEquals("bob.b32.i2p", handle.sent.peek().header("x.dest.i2p"));
    }
}
