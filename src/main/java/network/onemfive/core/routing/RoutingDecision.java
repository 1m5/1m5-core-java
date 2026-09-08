package network.onemfive.core.routing;

import network.onemfive.core.ManCon;
import ra.common.network.Network;
import ra.common.network.NetworkPeer;

import java.util.Collections;
import java.util.List;

/**
 * The outcome of {@link EscalationRouter#decide}: what the router should do with
 * one envelope, and why. Immutable; built by the escalation engine and applied by
 * {@link RoutingService}.
 */
public final class RoutingDecision {

    public enum Kind {
        /** Send directly over {@link #network}. */
        DIRECT,
        /** Send over {@link #network} to {@link #relayPeer}, who forwards to the destination. */
        RELAYED,
        /** No path right now - hold and retry. */
        HOLD,
        /** Give up - dead-letter. */
        DROP
    }

    public final Kind kind;
    public final Network network;          // DIRECT / RELAYED
    public final NetworkPeer destination;  // DIRECT P2P (null for web or undirected)
    public final NetworkPeer relayPeer;    // RELAYED
    public final ManCon effectiveManCon;
    public final List<Network> acceptable;
    public final String reason;

    private RoutingDecision(Kind kind, Network network, NetworkPeer destination, NetworkPeer relayPeer,
                            ManCon effectiveManCon, List<Network> acceptable, String reason) {
        this.kind = kind;
        this.network = network;
        this.destination = destination;
        this.relayPeer = relayPeer;
        this.effectiveManCon = effectiveManCon;
        this.acceptable = acceptable == null ? Collections.emptyList() : acceptable;
        this.reason = reason;
    }

    static RoutingDecision direct(Network network, NetworkPeer destination, ManCon mc,
                                  List<Network> acceptable, String reason) {
        return new RoutingDecision(Kind.DIRECT, network, destination, null, mc, acceptable, reason);
    }

    static RoutingDecision relayed(Network network, NetworkPeer relayPeer, NetworkPeer destination,
                                   ManCon mc, List<Network> acceptable, String reason) {
        return new RoutingDecision(Kind.RELAYED, network, destination, relayPeer, mc, acceptable, reason);
    }

    static RoutingDecision hold(ManCon mc, List<Network> acceptable, String reason) {
        return new RoutingDecision(Kind.HOLD, null, null, null, mc, acceptable, reason);
    }

    static RoutingDecision drop(ManCon mc, String reason) {
        return new RoutingDecision(Kind.DROP, null, null, null, mc, null, reason);
    }

    @Override
    public String toString() {
        return "RoutingDecision{" + kind
                + (network != null ? " via " + network : "")
                + (relayPeer != null ? " relay=" + relayPeer.getId() : "")
                + ", manCon=" + effectiveManCon
                + ", reason=" + reason + '}';
    }
}
