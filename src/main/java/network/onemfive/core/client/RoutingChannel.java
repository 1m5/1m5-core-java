package network.onemfive.core.client;

/**
 * Well-known channel/operation/header names for addressing {@code
 * network.onemfive.core.routing.RoutingService} through a plain {@link Msg} -
 * without an app-layer host ever importing that (bus-internal) package, per
 * {@code DESIGN.md} §"The embedding contract" / ADR 2 in {@code
 * 1m5-remnant/DESIGN.md} ("no bus primitive... visible above {@code
 * :core-host}"). Values must stay identical to {@code RoutingService}'s own
 * constants - checked by {@code RoutingChannelTest}, not shared directly,
 * since sharing would mean importing the bus-internal class this exists to
 * avoid.
 */
public final class RoutingChannel {

    /** {@code RoutingService.class.getName()} - the channel name to address it by. */
    public static final String NAME = "network.onemfive.core.routing.RoutingService";

    /** Mirrors {@code RoutingService.OPERATION_ROUTE}: send, letting the router pick a transport. */
    public static final String OPERATION_SEND = "ROUTE";

    /** Mirrors {@code RoutingService.OPERATION_REGISTER_PEER}: register a contact's known address(es). */
    public static final String OPERATION_REGISTER_PEER = "REGISTER_PEER";

    /** The destination peer's fingerprint - required for both operations above. */
    public static final String HEADER_DEST_PEER_ID = "x.dest.peerId";
    public static final String HEADER_DEST_I2P = "x.dest.i2p";
    public static final String HEADER_DEST_TOR = "x.dest.tor";
    public static final String HEADER_DEST_BT = "x.dest.bt";

    private RoutingChannel() {}
}
