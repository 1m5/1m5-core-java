package network.onemfive.core.client;

import ra.common.network.Network;

/**
 * A transport the host owns, registered with the core via
 * {@link CoreClient#registerProtocol(ProtocolHandle)}. The core discovers and
 * routes to it the same way it does any other {@code ProtocolService}; the host
 * never sees the bus, only this callback shape.
 *
 * <p>{@link #network()} is {@code ra.common.network.Network} rather than a
 * CoreClient-local type - a deliberate, narrow exception to "the host never sees a
 * bus primitive": {@code Network} is a plain enum with no bus/envelope shape, and
 * host-side transport code ({@code :core-host} in {@code 1m5-remnant}, never
 * {@code :app}) already needs it to answer "which network is this."
 */
public interface ProtocolHandle {

    /** Stable channel name, e.g. {@code "I2P"}. Used for discovery and routing. */
    String name();

    Network network();

    TransportStatus status();

    /** Carry a {@link Msg} out over this transport. False if the send could not be attempted. */
    boolean send(Msg msg);
}
