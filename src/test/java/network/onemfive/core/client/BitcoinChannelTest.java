package network.onemfive.core.client;

import network.onemfive.core.Core;
import network.onemfive.core.business.BitcoinService;
import network.onemfive.core.identity.IdentityService;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import ra.btc.BitcoinClient;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Catches drift between {@link BitcoinChannel}'s app-safe constants and {@code ra.btc.BitcoinClient}'s
 * real ones (mirrors {@link RoutingChannelTest}'s role for {@link RoutingChannel}), and proves the
 * actual round trip a host depends on: a {@link BitcoinChannel} response header set by
 * {@code BitcoinJClient.handleDocument} really does reach {@link EmbeddedCoreClient#send(Msg,
 * ReplyHandler)}'s callback, not just that the callback fires (which {@code
 * EmbeddedCoreClientTest#sendWithOperationInvokesTheNamedMethodOnABusinessChannel} already covers with
 * a mock service that never writes a response).
 */
public class BitcoinChannelTest {

    private EmbeddedCoreClient client;

    @Before
    public void setUp() {
        resetIdentityStorage(); // IdentityService persists at a fixed ~/.ra path, not a per-test tmp dir
        client = new EmbeddedCoreClient();
        Map<String, String> config = new HashMap<>();
        config.put(IdentityService.PROP_PASS, "test-pass-" + System.nanoTime());
        config.put("1m5.bitcoin.enabled", "true");
        Assert.assertTrue("core should start", client.start(config));
        // Not client.awaitReady(): that only polls Core.get().readyProtocols() (transport
        // channels), which BitcoinService - a BusinessService - never appears in. Mirrors
        // BitcoinServiceTest's own readiness check.
        Assert.assertTrue("BitcoinService should reach RUNNING",
                Core.get().awaitServices(15_000, BitcoinService.class));
    }

    /** Mirrors {@code EmbeddedCoreClientTest}'s cleanup - see that class for why. */
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
    public void mirrorsBitcoinClientExactly() {
        Assert.assertEquals(BitcoinService.class.getName(), BitcoinChannel.NAME);
        Assert.assertEquals(BitcoinClient.OPERATION_GET_BALANCE, BitcoinChannel.OPERATION_GET_BALANCE);
        Assert.assertEquals(BitcoinClient.OPERATION_GET_RECEIVE_ADDRESS, BitcoinChannel.OPERATION_GET_RECEIVE_ADDRESS);
        Assert.assertEquals(BitcoinClient.OPERATION_LIST_TRANSACTIONS, BitcoinChannel.OPERATION_LIST_TRANSACTIONS);
        Assert.assertEquals(BitcoinClient.OPERATION_SEND, BitcoinChannel.OPERATION_SEND);
        Assert.assertEquals(BitcoinClient.OPERATION_SYNC_STATUS, BitcoinChannel.OPERATION_SYNC_STATUS);
        Assert.assertEquals(BitcoinClient.HEADER_BALANCE_SATS, BitcoinChannel.HEADER_BALANCE_SATS);
        Assert.assertEquals(BitcoinClient.HEADER_AVAILABLE_SATS, BitcoinChannel.HEADER_AVAILABLE_SATS);
        Assert.assertEquals(BitcoinClient.HEADER_ADDRESS, BitcoinChannel.HEADER_ADDRESS);
        Assert.assertEquals(BitcoinClient.HEADER_AMOUNT_SATS, BitcoinChannel.HEADER_AMOUNT_SATS);
        Assert.assertEquals(BitcoinClient.HEADER_TXID, BitcoinChannel.HEADER_TXID);
        Assert.assertEquals(BitcoinClient.HEADER_SYNCING, BitcoinChannel.HEADER_SYNCING);
        Assert.assertEquals(BitcoinClient.HEADER_BEST_HEIGHT, BitcoinChannel.HEADER_BEST_HEIGHT);
    }

    @Test
    public void getBalanceResponseHeadersReachTheReplyCallback() throws Exception {
        CountDownLatch replied = new CountDownLatch(1);
        AtomicReference<Msg> replyRef = new AtomicReference<>();
        Msg msg = new Msg().to(BitcoinChannel.NAME, BitcoinChannel.OPERATION_GET_BALANCE);
        client.send(msg, reply -> { replyRef.set(reply); replied.countDown(); });

        Assert.assertTrue("reply callback should fire", replied.await(15, TimeUnit.SECONDS));
        Msg reply = replyRef.get();
        Assert.assertNotNull(reply);
        Assert.assertEquals("0", reply.header(BitcoinChannel.HEADER_AVAILABLE_SATS));
        Assert.assertEquals("0", reply.header(BitcoinChannel.HEADER_BALANCE_SATS));
    }

    @Test
    public void getReceiveAddressResponseReachesTheReplyCallback() throws Exception {
        CountDownLatch replied = new CountDownLatch(1);
        AtomicReference<Msg> replyRef = new AtomicReference<>();
        Msg msg = new Msg().to(BitcoinChannel.NAME, BitcoinChannel.OPERATION_GET_RECEIVE_ADDRESS);
        client.send(msg, reply -> { replyRef.set(reply); replied.countDown(); });

        Assert.assertTrue("reply callback should fire", replied.await(15, TimeUnit.SECONDS));
        String address = replyRef.get().header(BitcoinChannel.HEADER_ADDRESS);
        Assert.assertNotNull(address);
        Assert.assertFalse(address.isEmpty());
    }

    @Test
    public void sendWithAnInvalidAddressReportsAnErrorInTheReply() throws Exception {
        CountDownLatch replied = new CountDownLatch(1);
        AtomicReference<Msg> replyRef = new AtomicReference<>();
        Msg msg = new Msg()
                .to(BitcoinChannel.NAME, BitcoinChannel.OPERATION_SEND)
                .header(BitcoinChannel.HEADER_ADDRESS, "not-a-real-address")
                .header(BitcoinChannel.HEADER_AMOUNT_SATS, "1000");
        client.send(msg, reply -> { replyRef.set(reply); replied.countDown(); });

        Assert.assertTrue("reply callback should fire", replied.await(15, TimeUnit.SECONDS));
        Msg reply = replyRef.get();
        Assert.assertNull(reply.header(BitcoinChannel.HEADER_TXID));
        Assert.assertNotNull("x.error.message should surface the failure - the same header "
                + "MessagingRepository.deliver already checks", reply.header("x.error.message"));
    }
}