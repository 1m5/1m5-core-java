package network.onemfive.core.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

import java.net.InetSocketAddress;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * A minimal, in-process NIP-01 relay for {@link NostrChannelTest} - no live
 * network, full control over what the "relay" says back. Trimmed copy of
 * {@code did-java}'s own test-only {@code ra.did.nostr.relay.FakeRelay}
 * (that class is test-scoped there too, so not reusable across modules).
 */
final class FakeRelay extends WebSocketServer {

    volatile Function<String, Object[]> onEvent = json -> new Object[] {true, ""};
    private final CountDownLatch ready = new CountDownLatch(1);

    FakeRelay(int port) {
        super(new InetSocketAddress("127.0.0.1", port));
        setReuseAddr(true);
    }

    void awaitReady() throws InterruptedException {
        if (!ready.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("fake relay never started");
    }

    @Override public void onStart() { ready.countDown(); }
    @Override public void onOpen(WebSocket conn, ClientHandshake handshake) {}
    @Override public void onClose(WebSocket conn, int code, String reason, boolean remote) {}
    @Override public void onError(WebSocket conn, Exception ex) {}

    @Override
    public void onMessage(WebSocket conn, String message) {
        JsonArray envelope = JsonParser.parseString(message).getAsJsonArray();
        String type = envelope.get(0).getAsString();
        if ("EVENT".equals(type)) {
            String eventJson = envelope.get(1).toString();
            String eventId = envelope.get(1).getAsJsonObject().get("id").getAsString();
            Object[] outcome = onEvent.apply(eventJson);
            conn.send("[\"OK\",\"" + eventId + "\"," + outcome[0] + ",\"" + outcome[1] + "\"]");
        } else if ("REQ".equals(type)) {
            String subId = envelope.get(1).getAsString();
            conn.send("[\"EOSE\",\"" + subId + "\"]");
        }
    }
}