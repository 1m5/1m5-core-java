package network.onemfive.core.service;

import ra.common.Envelope;
import ra.common.network.Network;
import ra.common.network.NetworkStatus;

/**
 * The contract every external transport adapter exposes to the router. Keeps
 * routing decisions ({@code RoutingService}) decoupled from transport
 * implementations (I2P, Tor, HTTP, Bluetooth, embedded vs. local, per platform).
 *
 * The router asks a Transport what it can do; it never talks to a platform
 * networking API directly.
 */
public interface Transport {

    /** Which {@link Network} this adapter speaks. */
    Network getNetwork();

    /** Current connectivity, updated by the adapter as the network changes. */
    NetworkStatus getNetworkStatus();

    /** Convenience: ready to carry an envelope right now. */
    default boolean isReady() {
        return getNetworkStatus() == NetworkStatus.CONNECTED
                || getNetworkStatus() == NetworkStatus.VERIFIED;
    }

    /**
     * Send an envelope out over this network. The routing slip's current route is
     * a {@link ra.common.route.ExternalRoute} carrying origination/destination
     * peers. Returns false if the send could not be attempted (caller decides
     * whether to hold, retry, or escalate).
     */
    boolean send(Envelope envelope);
}
