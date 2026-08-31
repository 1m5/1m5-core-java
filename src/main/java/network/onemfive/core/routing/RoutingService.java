package network.onemfive.core.routing;

import network.onemfive.core.Core;
import network.onemfive.core.ManCon;
import network.onemfive.core.SituationalAwareness;
import network.onemfive.core.service.BusinessService;
import network.onemfive.core.service.ProtocolService;
import ra.common.Envelope;
import ra.common.route.ExternalRoute;
import ra.common.route.Route;

import java.util.List;
import java.util.logging.Logger;

/**
 * The 1M5 intelligent router, as a business service on the bus.
 *
 * Envelopes bound for a peer or a URL are routed here first (add a
 * {@code RoutingService.ROUTE} hop). The router reads the envelope's sensitivity
 * ({@link ManCon}), the {@link network.onemfive.core.ManConStatus} band, and which
 * {@link ProtocolService}s are currently connected, then pushes the concrete
 * transport hop onto the {@link ra.common.route.DynamicRoutingSlip}. The SEDA bus
 * engine ({@code SEDABus.completed}) advances the slip and carries the envelope on.
 *
 * <p><b>Skeleton scope:</b> this chooses one ready protocol and delegates. The
 * full escalation logic - ManCon x web/P2P x Tor/I2P/Bluetooth relay selection,
 * random delays, NEO multi-copy, message hold/retry - is ported from
 * {@code onemfive.routing.CRNetworkManagerService} in a later phase (see TODO.md).
 *
 * <p>Router pattern: decide <b>one hop at a time</b>. The slip is a LIFO stack, so
 * pushing a full multi-hop itinerary in one call would execute it in reverse.
 */
public final class RoutingService extends BusinessService {

    private static final Logger LOG = Logger.getLogger(RoutingService.class.getName());

    public static final String OPERATION_ROUTE = "ROUTE";

    @Override
    public void handleDocument(Envelope envelope) {
        route(envelope);
    }

    @Override
    public void handleHeaders(Envelope envelope) {
        route(envelope);
    }

    private void route(Envelope envelope) {
        SituationalAwareness sa = assess(envelope);
        LOG.info("routing " + envelope.getId() + " " + sa);

        ProtocolService chosen = choose(envelope, sa);
        if (chosen == null) {
            String msg = "no connected ProtocolService for ManCon " + sa.selectedManCon
                    + "; envelope " + envelope.getId() + " not routed (hold/retry is a TODO)";
            LOG.warning(msg);
            envelope.addErrorMessage(msg);
            return;
        }

        applyManConParameters(envelope, sa.selectedManCon);
        envelope.addExternalRoute(chosen.getClass().getName(), ProtocolService.OPERATION_SEND);
        LOG.info("routed " + envelope.getId() + " via " + chosen.getClass().getSimpleName()
                + " (" + chosen.getNetwork() + ")");
    }

    private SituationalAwareness assess(Envelope envelope) {
        SituationalAwareness sa = new SituationalAwareness();
        sa.isWebRequest = envelope.getURL() != null;
        sa.envelopeSensitivity = envelope.getSensitivity() == null ? 0 : envelope.getSensitivity();
        sa.envelopeManCon = ManCon.fromSensitivity(sa.envelopeSensitivity);
        sa.selectedManCon = Core.get().manConStatus().select(sa.envelopeManCon);
        sa.withinMaxAvailable =
                sa.envelopeManCon.ordinal() >= Core.get().manConStatus().getMaxAvailable().ordinal();
        sa.withinMinRequired =
                sa.envelopeManCon.ordinal() <= Core.get().manConStatus().getMinRequired().ordinal();
        return sa;
    }

    /**
     * If the next slip route already names a ready protocol, keep it; otherwise
     * pick the first ready protocol. (Full network selection by ManCon is a TODO.)
     */
    private ProtocolService choose(Envelope envelope, SituationalAwareness sa) {
        Route next = envelope.getDynamicRoutingSlip().peekAtNextRoute();
        if (next instanceof ExternalRoute && next.getService() != null) {
            for (ProtocolService p : Core.get().protocols()) {
                if (p.getClass().getName().equals(next.getService()) && p.isReady()) {
                    return p;
                }
            }
        }
        List<ProtocolService> ready = Core.get().readyProtocols();
        return ready.isEmpty() ? null : ready.get(0);
    }

    private void applyManConParameters(Envelope envelope, ManCon manCon) {
        switch (manCon) {
            case VERYHIGH:
                envelope.setDelayed(true);
                envelope.setMinDelay(4 * 1000);
                envelope.setMaxDelay(10 * 1000);
                break;
            case EXTREME:
                envelope.setDelayed(true);
                envelope.setMinDelay(10 * 1000);
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
