package network.onemfive.core.routing;

import network.onemfive.core.Core;
import network.onemfive.core.ManCon;
import network.onemfive.core.ManConStatus;
import network.onemfive.core.SituationalAwareness;
import network.onemfive.core.service.BusinessService;
import network.onemfive.core.service.ProtocolService;
import ra.common.Envelope;
import ra.common.network.Network;
import ra.common.network.NetworkPeer;
import ra.common.route.ExternalRoute;
import ra.common.route.RelayedExternalRoute;
import ra.common.route.Route;

import java.net.URL;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * The 1M5 intelligent router, as a business service on the bus.
 *
 * <p>Envelopes bound for a peer or a URL are routed here first (add a
 * {@code RoutingService.ROUTE} hop). For each one the router:
 *
 * <ol>
 *   <li>refreshes {@link ManConStatus#getMaxAvailable()} from which transports are
 *       currently ready, firing {@link network.onemfive.core.ManConStatusListener}s
 *       on a change;</li>
 *   <li>asks the pure {@link EscalationRouter} for a {@link RoutingDecision} -
 *       ManCon &times; (web | P2P) network selection, address-matched transport
 *       choice, cross-transport relay;</li>
 *   <li>applies it: push the transport hop (SEDA carries the envelope on), or
 *       <b>hold</b> the envelope and schedule a retry with backoff
 *       ({@link RetryStrategy}), or <b>dead-letter</b> it once the retry window is
 *       exhausted.</li>
 * </ol>
 *
 * <p>The full {@link SituationalAwareness} snapshot is logged for every envelope
 * so any decision can be explained.
 *
 * <p><b>Held-envelope note:</b> when the router holds an envelope it returns
 * without pushing a hop, so the SEDA slip completes and a
 * {@code send(envelope, callback)} callback fires early (as it does in Remnant's
 * {@code RouterService}). The retry re-submits the same envelope fire-and-forget.
 */
public final class RoutingService extends BusinessService {

    private static final Logger LOG = Logger.getLogger(RoutingService.class.getName());

    public static final String OPERATION_ROUTE = "ROUTE";

    /** Optional header naming the destination peer's DID fingerprint for P2P routing. */
    public static final String HEADER_DEST_FINGERPRINT = "1m5.route.dest";
    /** Reserved {@code CoreClient} header carrying the same thing. */
    public static final String HEADER_X_DEST_PEER = "x.dest.peerId";

    private final EscalationRouter engine = new EscalationRouter();
    private final PeerDirectory peers = new PeerDirectory();

    private volatile RetryStrategy retryStrategy = RetryStrategy.normalMessaging();
    private volatile RetryScheduler retryScheduler;

    // envelopeId -> held envelope awaiting a path
    private final Map<String, Held> held = new ConcurrentHashMap<>();

    private static final class Held {
        final Envelope envelope;
        final long firstHeldAt;
        long currentDelay;
        int attempts;
        Held(Envelope envelope, long now) { this.envelope = envelope; this.firstHeldAt = now; }
    }

    // -- lifecycle -------------------------------------------------

    @Override
    public boolean start(Properties p) {
        if (!super.start(p)) return false;
        if (retryScheduler == null) retryScheduler = new RetryScheduler.Default();
        refreshAvailability();
        return true;
    }

    @Override
    public boolean shutdown() {
        if (retryScheduler != null) retryScheduler.shutdown();
        held.clear();
        return super.shutdown();
    }

    // -- test / host hooks ---------------------------------------

    public PeerDirectory peerDirectory() { return peers; }

    public void setRetryStrategy(RetryStrategy s) { this.retryStrategy = s; }

    public void setRetryScheduler(RetryScheduler s) {
        RetryScheduler old = this.retryScheduler;
        this.retryScheduler = s;
        if (old != null && old != s) old.shutdown();
    }

    /** Number of envelopes currently held awaiting a path (routing diagnostics). */
    public int heldCount() { return held.size(); }

    public boolean isHeld(String envelopeId) { return held.containsKey(envelopeId); }

    // -- bus entry points --------------------------------------

    @Override
    public void handleDocument(Envelope envelope) {
        route(envelope);
    }

    @Override
    public void handleHeaders(Envelope envelope) {
        route(envelope);
    }

    // -- routing --------------------------------------------------

    private void route(Envelope envelope) {
        refreshAvailability();

        SituationalAwareness sa = new SituationalAwareness();
        Held h = held.get(envelope.getId());

        Facts f = facts(envelope);
        sa.isWebRequest = f.webRequest;
        sa.localhostUrl = f.localhostUrl;
        sa.envelopeSensitivity = f.sensitivity;
        sa.envelopeManCon = f.requestedManCon;
        sa.destinationFingerprint = f.destinationFingerprint;

        ManConStatus mcs = Core.get().manConStatus();
        sa.minRequired = mcs.getMinRequired();
        sa.maxAvailable = mcs.getMaxAvailable();

        Set<Network> ready = readyNetworks();
        sa.readyNetworks = new ArrayList<>(ready);

        RoutingDecision d = engine.decide(f.webRequest, f.localhostUrl, f.requestedManCon,
                f.destinationFingerprint, f.explicitDestination, mcs, ready, peers);

        sa.selectedManCon = d.effectiveManCon;
        sa.meetsFloor = mcs.meetsFloor(d.effectiveManCon);
        sa.withinMaxAvailable = d.effectiveManCon.ordinal() >= sa.maxAvailable.ordinal();
        sa.acceptableNetworks = d.acceptable;
        sa.decision = d.kind.name();
        sa.chosenNetwork = d.network;
        sa.reason = d.reason;
        if (h != null) sa.attempt = h.attempts + 1;

        switch (d.kind) {
            case DIRECT:
                dispatchDirect(envelope, d, sa);
                clearHold(envelope.getId());
                break;
            case RELAYED:
                sa.relayNetwork = d.network;
                sa.relayPeerFingerprint = d.relayPeer != null ? d.relayPeer.getId() : null;
                dispatchRelayed(envelope, d, sa);
                clearHold(envelope.getId());
                break;
            case DROP:
                LOG.warning("dead-letter " + envelope.getId() + " " + sa);
                envelope.addErrorMessage("routing: " + d.reason);
                clearHold(envelope.getId());
                break;
            case HOLD:
            default:
                hold(envelope, sa);
                break;
        }
    }

    private void dispatchDirect(Envelope envelope, RoutingDecision d, SituationalAwareness sa) {
        ProtocolService proto = readyProtocolFor(d.network);
        if (proto == null) { // lost the race since decide()
            hold(envelope, sa);
            return;
        }
        applyManConParameters(envelope, d.effectiveManCon);
        String channel = Core.get().resolveChannel(proto.channelName());
        if (d.destination != null) {
            envelope.addExternalRoute(channel, ProtocolService.OPERATION_SEND, null, d.destination);
        } else {
            envelope.addExternalRoute(channel, ProtocolService.OPERATION_SEND);
        }
        LOG.info("routed " + envelope.getId() + " " + sa);
    }

    private void dispatchRelayed(Envelope envelope, RoutingDecision d, SituationalAwareness sa) {
        ProtocolService proto = readyProtocolFor(d.network);
        if (proto == null) {
            hold(envelope, sa);
            return;
        }
        applyManConParameters(envelope, d.effectiveManCon);
        RelayedExternalRoute rr = new RelayedExternalRoute();
        rr.setService(Core.get().resolveChannel(proto.channelName()));
        rr.setOperation(ProtocolService.OPERATION_SEND);
        rr.setFromPeer(d.relayPeer);
        rr.setToPeer(d.destination);
        rr.setSensitivity(ManCon.toSensitivity(d.effectiveManCon));
        envelope.getDynamicRoutingSlip().addRoute(rr);
        LOG.info("routed(relay) " + envelope.getId() + " " + sa);
    }

    // -- hold + retry -------------------------------------------

    private void hold(Envelope envelope, SituationalAwareness sa) {
        long now = System.currentTimeMillis();
        String id = envelope.getId();
        Held h = held.computeIfAbsent(id, k -> new Held(envelope, now));
        long elapsed = now - h.firstHeldAt;

        if (h.attempts > 0 && retryStrategy.giveUp(elapsed)) {
            sa.decision = "DROP";
            sa.reason = "retry window exhausted after " + (elapsed / 1000) + "s / "
                    + h.attempts + " attempts (" + sa.reason + ")";
            LOG.warning("dead-letter " + id + " " + sa);
            envelope.addErrorMessage("routing: " + sa.reason);
            clearHold(id);
            return;
        }

        h.currentDelay = retryStrategy.nextDelayMs(elapsed, h.currentDelay);
        h.attempts++;
        LOG.info("hold " + id + " retry in " + (h.currentDelay / 1000) + "s " + sa);
        retryScheduler.schedule(id, h.currentDelay, () -> reroute(id));
    }

    private void reroute(String envelopeId) {
        Held h = held.get(envelopeId);
        if (h == null) return; // already routed or dropped
        Envelope e = h.envelope;
        e.getMessage().clearErrorMessages();
        e.addRoute(RoutingService.class, OPERATION_ROUTE);
        e.ratchet();
        LOG.fine("re-routing held envelope " + envelopeId + " (attempt " + h.attempts + ")");
        Core.get().send(e);
    }

    private void clearHold(String envelopeId) {
        if (held.remove(envelopeId) != null && retryScheduler != null) {
            retryScheduler.cancel(envelopeId);
        }
    }

    // -- ManCon availability -----------------------------------

    /** Recompute {@code maxAvailable} from ready transports; fire listeners on change. */
    public void refreshAvailability() {
        ManCon now = ManConNetworks.maxAvailableFor(readyNetworks());
        if (Core.get().manConStatus().setMaxAvailable(now)) {
            LOG.info("ManCon maxAvailable -> " + now);
            Core.get().manConStatusChanged();
        }
    }

    // -- helpers ------------------------------------------------

    private static final class Facts {
        boolean webRequest;
        boolean localhostUrl;
        int sensitivity;
        ManCon requestedManCon;
        String destinationFingerprint;
        NetworkPeer explicitDestination;
    }

    private Facts facts(Envelope envelope) {
        Facts f = new Facts();
        URL url = envelope.getURL();
        f.webRequest = url != null;
        f.localhostUrl = url != null
                && (String.valueOf(url.getHost()).contains("127.0.0.1")
                    || "localhost".equalsIgnoreCase(url.getHost()));
        f.sensitivity = envelope.getSensitivity() == null ? 0 : envelope.getSensitivity();
        f.requestedManCon = ManCon.fromSensitivity(f.sensitivity);

        Route next = envelope.getDynamicRoutingSlip().peekAtNextRoute();
        if (next instanceof ExternalRoute) {
            NetworkPeer dst = ((ExternalRoute) next).getDestination();
            if (dst != null) {
                f.explicitDestination = dst;
                f.destinationFingerprint = dst.getId();
            }
        }
        if (f.destinationFingerprint == null) {
            Object v = envelope.getHeader(HEADER_DEST_FINGERPRINT);
            if (v == null) v = envelope.getHeader(HEADER_X_DEST_PEER);
            if (v != null) f.destinationFingerprint = String.valueOf(v);
        }
        return f;
    }

    private Set<Network> readyNetworks() {
        Set<Network> out = EnumSet.noneOf(Network.class);
        for (ProtocolService p : Core.get().readyProtocols()) {
            if (p.getNetwork() != null) out.add(p.getNetwork());
        }
        return out;
    }

    private ProtocolService readyProtocolFor(Network network) {
        for (ProtocolService p : Core.get().readyProtocols()) {
            if (p.getNetwork() == network) return p;
        }
        return null;
    }

    private void applyManConParameters(Envelope envelope, ManCon manCon) {
        switch (manCon) {
            case VERYHIGH:
                envelope.setDelayed(true);
                envelope.setMinDelay(4 * 1000);
                envelope.setMaxDelay(8 * 1000);
                break;
            case EXTREME:
                envelope.setDelayed(true);
                envelope.setMinDelay(15 * 1000);
                envelope.setMaxDelay(30 * 1000);
                break;
            case NEO:
                envelope.setDelayed(true);
                envelope.setMinDelay(60 * 1000);
                envelope.setMaxDelay(2 * 60 * 1000);
                envelope.setCopy(true);
                envelope.setMinCopies(3);
                envelope.setMaxCopies(12);
                break;
            default:
                // LOW / MEDIUM / HIGH: no added delay or copies at this layer
        }
    }
}
