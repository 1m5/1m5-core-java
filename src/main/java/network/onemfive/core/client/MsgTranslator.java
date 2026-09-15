package network.onemfive.core.client;

import network.onemfive.core.Core;
import network.onemfive.core.service.ProtocolService;
import ra.common.Envelope;
import ra.common.network.Network;
import ra.common.network.NetworkPeer;
import ra.common.route.ExternalRoute;
import ra.common.service.ServiceLevel;

import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * {@link Msg} &harr; {@code ra.common.Envelope} translation, shared by
 * {@link EmbeddedCoreClient} (outbound app &rarr; core) and
 * {@link HandleBackedProtocolService} (outbound core &rarr; transport, and the
 * transport's {@link CoreInbound#accept(Msg)} path).
 *
 * <p>Each {@code Msg.Hop} maps directly to {@code Route.getService()} /
 * {@code Route.getOperation()} - the channel and the method on it. Reserved
 * {@code x.*} headers map to {@code Envelope} scalar setters; every other header
 * passes through unchanged to the {@code Envelope}'s own header map - an
 * operation's own named parameters (e.g. a Bitcoin send amount/address), per
 * {@code DESIGN.md} §"Non-transport core capabilities".
 *
 * <p><b>Known gap:</b> {@code x.relay.peerId} and {@code x.error.*} are round-
 * tripped as plain headers but do not yet drive {@code RelayedExternalRoute}
 * construction on the outbound path - the router builds those itself when it
 * decides to relay. Revisit once an app-originated relay hint is a real use case.
 */
final class MsgTranslator {

    private static final Logger LOG = Logger.getLogger(MsgTranslator.class.getName());

    private static final String X_SENSITIVITY = "x.sensitivity";
    private static final String X_URL = "x.url";
    private static final String X_SERVICE_LEVEL = "x.serviceLevel";
    private static final String X_DELAYED = "x.delayed";
    private static final String X_MIN_DELAY_MS = "x.minDelayMs";
    private static final String X_MAX_DELAY_MS = "x.maxDelayMs";
    private static final String X_COPY = "x.copy";
    private static final String X_MIN_COPIES = "x.minCopies";
    private static final String X_MAX_COPIES = "x.maxCopies";
    private static final String X_DEST_PEER_ID = "x.dest.peerId";
    private static final String X_DEST_I2P = "x.dest.i2p";
    private static final String X_DEST_TOR = "x.dest.tor";
    private static final String X_DEST_BT = "x.dest.bt";

    // Envelope has no first-class "sender" / "attempts" field; carried as plain
    // internal headers rather than reserved x.* ones (they are not routing scalars).
    private static final String HEADER_MSG_SENDER = "msg.sender";
    private static final String HEADER_MSG_ATTEMPTS = "msg.attempts";

    private MsgTranslator() {}

    // -- Msg -> Envelope (outbound) --------------------------------

    static Envelope toEnvelope(Msg msg) {
        Envelope e = msg.getId() != null ? Envelope.documentFactory(msg.getId()) : Envelope.documentFactory();
        if (msg.getPayload() != null) e.addContent(msg.getPayload());
        if (msg.getSender() != null) e.setHeader(HEADER_MSG_SENDER, msg.getSender());
        e.setHeader(HEADER_MSG_ATTEMPTS, String.valueOf(msg.getAttempts()));

        String destPeerId = null, destI2p = null, destTor = null, destBt = null;
        for (Map.Entry<String, String> h : msg.getHeaders().entrySet()) {
            String k = h.getKey();
            String v = h.getValue();
            if (v == null) continue;
            switch (k) {
                case X_SENSITIVITY: e.setSensitivity(Integer.valueOf(v)); break;
                case X_URL:
                    try { e.setURL(new URL(v)); } catch (MalformedURLException ex) {
                        LOG.warning("bad " + X_URL + ": " + v);
                    }
                    break;
                case X_SERVICE_LEVEL: e.setServiceLevel(ServiceLevel.valueOf(v)); break;
                case X_DELAYED: e.setDelayed(Boolean.valueOf(v)); break;
                case X_MIN_DELAY_MS: e.setMinDelay(Integer.valueOf(v)); break;
                case X_MAX_DELAY_MS: e.setMaxDelay(Integer.valueOf(v)); break;
                case X_COPY: e.setCopy(Boolean.valueOf(v)); break;
                case X_MIN_COPIES: e.setMinCopies(Integer.valueOf(v)); break;
                case X_MAX_COPIES: e.setMaxCopies(Integer.valueOf(v)); break;
                case X_DEST_PEER_ID: destPeerId = v; break;
                case X_DEST_I2P: destI2p = v; break;
                case X_DEST_TOR: destTor = v; break;
                case X_DEST_BT: destBt = v; break;
                default:
                    e.setHeader(k, v); // an operation's own named parameters, or x.relay.*/x.error.* - see class javadoc
            }
        }

        // Also surfaced as plain headers (not just folded into an ExternalRoute
        // below) so a business channel - RoutingService's OPERATION_ROUTE reading
        // x.dest.peerId as its destination fingerprint, or OPERATION_REGISTER_PEER
        // reading all four to populate PeerDirectory - can see them too. Without
        // this, addressing a non-protocol channel (which is exactly what the app
        // does when it wants the router to resolve the protocol, not name one
        // itself) silently dropped every x.dest.* header on the floor.
        if (destPeerId != null) e.setHeader(X_DEST_PEER_ID, destPeerId);
        if (destI2p != null) e.setHeader(X_DEST_I2P, destI2p);
        if (destTor != null) e.setHeader(X_DEST_TOR, destTor);
        if (destBt != null) e.setHeader(X_DEST_BT, destBt);

        // NetworkPeer has no no-arg constructor - it needs a Network up front, so a
        // destination is only buildable when one of the network-scoped address
        // headers is present; a bare x.dest.peerId with no network hint is dropped.
        NetworkPeer destination = null;
        String destAddress = null;
        if (destI2p != null) { destination = new NetworkPeer(Network.I2P); destAddress = destI2p; }
        else if (destTor != null) { destination = new NetworkPeer(Network.Tor); destAddress = destTor; }
        else if (destBt != null) { destination = new NetworkPeer(Network.Bluetooth); destAddress = destBt; }
        if (destination != null) {
            if (destPeerId != null) destination.setId(destPeerId);
            if (destAddress != null) destination.getDid().getPublicKey().setAddress(destAddress);
        }

        List<Msg.Hop> chain = new ArrayList<>(msg.getSlip());

        // Every intermediate hop is a protocol/relay channel, which only ever
        // implements one method (OPERATION_SEND) regardless of what its Hop says;
        // only the last hop's own operation is honored, since it's the actual
        // destination service.
        for (int i = chain.size() - 1; i >= 0; i--) { // push last hop first: LIFO, front pops first
            Msg.Hop hop = chain.get(i);
            boolean last = i == chain.size() - 1;
            if (isProtocolChannel(hop.getChannel())) {
                if (last && destination != null) {
                    e.addExternalRoute(hop.getChannel(), ProtocolService.OPERATION_SEND, null, destination);
                } else {
                    e.addExternalRoute(hop.getChannel(), ProtocolService.OPERATION_SEND);
                }
            } else {
                String op = last && hop.getOperation() != null ? hop.getOperation() : ProtocolService.OPERATION_SEND;
                e.addRoute(hop.getChannel(), op);
            }
        }
        if (!chain.isEmpty()) e.ratchet();
        return e;
    }

    private static boolean isProtocolChannel(String channelName) {
        for (ProtocolService p : Core.get().protocols()) {
            if (channelName.equals(p.channelName())) return true;
        }
        return false;
    }

    // -- Envelope -> Msg (reply / inbound) --------------------------

    static Msg toMsg(Envelope e) {
        Msg m = new Msg();
        m.setId(e.getId());
        Object content = e.getContent();
        if (content instanceof byte[]) m.setPayload((byte[]) content);
        else if (content instanceof String) m.setPayload(((String) content).getBytes(StandardCharsets.UTF_8));

        Map<String, String> headers = new LinkedHashMap<>();
        if (e.getHeaders() != null) {
            for (Map.Entry<String, Object> h : e.getHeaders().entrySet()) {
                if (h.getKey().equals(HEADER_MSG_SENDER)) { m.setSender(String.valueOf(h.getValue())); continue; }
                if (h.getKey().equals(HEADER_MSG_ATTEMPTS)) {
                    try { m.setAttempts(Integer.parseInt(String.valueOf(h.getValue()))); } catch (NumberFormatException ignored) {}
                    continue;
                }
                headers.put(h.getKey(), String.valueOf(h.getValue()));
            }
        }
        if (e.getSensitivity() != null) headers.put(X_SENSITIVITY, String.valueOf(e.getSensitivity()));
        if (e.getURL() != null) headers.put(X_URL, e.getURL().toString());
        if (e.getServiceLevel() != null) headers.put(X_SERVICE_LEVEL, e.getServiceLevel().name());
        if (e.getDelayed() != null) headers.put(X_DELAYED, String.valueOf(e.getDelayed()));
        if (e.getMinDelay() != null) headers.put(X_MIN_DELAY_MS, String.valueOf(e.getMinDelay()));
        if (e.getMaxDelay() != null) headers.put(X_MAX_DELAY_MS, String.valueOf(e.getMaxDelay()));
        if (e.getCopy() != null) headers.put(X_COPY, String.valueOf(e.getCopy()));
        if (e.getMinCopies() != null) headers.put(X_MIN_COPIES, String.valueOf(e.getMinCopies()));
        if (e.getMaxCopies() != null) headers.put(X_MAX_COPIES, String.valueOf(e.getMaxCopies()));

        List<String> errors = Envelope.getErrorMessages(e);
        if (errors != null && !errors.isEmpty()) headers.put("x.error.message", String.join("; ", errors));

        if (e.getRoute() instanceof ExternalRoute) {
            NetworkPeer dest = ((ExternalRoute) e.getRoute()).getDestination();
            if (dest != null && dest.getId() != null) headers.put(X_DEST_PEER_ID, dest.getId());
            // The network-specific address (not just the peer id) - a ProtocolHandle
            // needs this to actually address the send; without it, a real transport
            // adapter has nowhere to deliver to.
            if (dest != null && dest.getNetwork() != null && dest.getDid() != null
                    && dest.getDid().getPublicKey() != null && dest.getDid().getPublicKey().getAddress() != null) {
                String address = dest.getDid().getPublicKey().getAddress();
                switch (dest.getNetwork()) {
                    case I2P: headers.put(X_DEST_I2P, address); break;
                    case Tor: headers.put(X_DEST_TOR, address); break;
                    case Bluetooth: headers.put(X_DEST_BT, address); break;
                    default: break;
                }
            }
        }

        m.setHeaders(headers);
        if (e.getRoute() != null && e.getRoute().getService() != null) {
            Deque<Msg.Hop> slip = new ArrayDeque<>();
            slip.addLast(new Msg.Hop(e.getRoute().getService(), e.getRoute().getOperation()));
            m.setSlip(slip);
        }
        return m;
    }
}
