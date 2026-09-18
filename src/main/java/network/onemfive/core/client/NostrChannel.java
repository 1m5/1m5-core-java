package network.onemfive.core.client;

/**
 * Well-known channel/operation/header names for addressing {@code
 * network.onemfive.core.business.NostrService} through a plain {@link Msg} -
 * without an app-layer host ever importing that (bus-internal) package, per
 * {@code DESIGN.md} §"The embedding contract" / ADR 2 in {@code
 * 1m5-remnant/DESIGN.md}. Same pattern as {@link BitcoinChannel}: values must
 * stay identical to {@code ra.did.nostr.relay.NostrRelayOps}'s own constants -
 * checked by {@code NostrChannelTest}, not shared directly, since sharing
 * would mean importing a {@code did-java} relay type this exists to avoid.
 *
 * <p>Deliberately generic - publish/query an already-signed event, not "post a
 * note" or "follow someone". Kind-specific event construction (kind 0/1/3
 * profile/notes/follows, kind 30100 attestations, kinds 30101-30103 guardian
 * events) is a {@code did-java} {@code ra.did.nostr} concern, one layer below
 * this channel; a host builds and signs the event itself (it already has
 * direct {@code did-java} access for the user identity - see
 * {@code 1m5-remnant/DESIGN.md} §"Identity") and hands only the signed JSON
 * across this boundary.
 */
public final class NostrChannel {

    /** {@code network.onemfive.core.business.NostrService.class.getName()} - the channel name to address it by. */
    public static final String NAME = "network.onemfive.core.business.NostrService";

    public static final String OPERATION_PUBLISH = "PUBLISH";
    public static final String OPERATION_QUERY = "QUERY";
    public static final String OPERATION_STATUS = "STATUS";

    public static final String HEADER_EVENT_JSON = "nostr.eventJson";
    public static final String HEADER_RESULTS_JSON = "nostr.resultsJson";
    public static final String HEADER_FILTER_JSON = "nostr.filterJson";
    public static final String HEADER_TIMEOUT_MS = "nostr.timeoutMs";
    public static final String HEADER_EVENTS_JSON = "nostr.eventsJson";
    public static final String HEADER_CONNECTED_RELAYS = "nostr.connectedRelays";
    public static final String HEADER_TOTAL_RELAYS = "nostr.totalRelays";

    private NostrChannel() {}
}
