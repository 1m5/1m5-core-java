package network.onemfive.core.client;

/**
 * Well-known channel/operation/header names for addressing {@code
 * network.onemfive.core.business.BitcoinService} through a plain {@link Msg} -
 * without an app-layer host ever importing that (bus-internal) package, per
 * {@code DESIGN.md} §"The embedding contract" / ADR 2 in {@code
 * 1m5-remnant/DESIGN.md} ("no bus primitive... visible above {@code
 * :core-host}"). Same pattern as {@link RoutingChannel}: values must stay
 * identical to {@code ra.btc.BitcoinClient}'s own constants - checked by
 * {@code BitcoinChannelTest}, not shared directly, since sharing would mean
 * importing a {@code bitcoin-client-java} type this exists to avoid.
 */
public final class BitcoinChannel {

    /**
     * {@code network.onemfive.core.business.BitcoinService.class.getName()} -
     * the channel name to address it by. Not {@code ra.btc.BitcoinService}
     * (the wrapped {@code bitcoin-client-java} client, never registered on the
     * bus directly) and not the illustrative {@code "Bitcoin"} string from
     * {@link Msg}'s own javadoc, which is not wired to anything.
     */
    public static final String NAME = "network.onemfive.core.business.BitcoinService";

    public static final String OPERATION_GET_BALANCE = "GET_BALANCE";
    public static final String OPERATION_GET_RECEIVE_ADDRESS = "GET_RECEIVE_ADDRESS";
    public static final String OPERATION_LIST_TRANSACTIONS = "LIST_TRANSACTIONS";
    public static final String OPERATION_SEND = "SEND";
    public static final String OPERATION_SYNC_STATUS = "SYNC_STATUS";

    public static final String HEADER_BALANCE_SATS = "btc.balanceSats";
    public static final String HEADER_AVAILABLE_SATS = "btc.availableSats";
    public static final String HEADER_ADDRESS = "btc.address";
    public static final String HEADER_AMOUNT_SATS = "btc.amountSats";
    public static final String HEADER_TXID = "btc.txid";
    public static final String HEADER_SYNCING = "btc.syncing";
    public static final String HEADER_BEST_HEIGHT = "btc.bestHeight";

    private BitcoinChannel() {}
}