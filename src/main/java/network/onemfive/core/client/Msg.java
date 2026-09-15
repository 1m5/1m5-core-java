package network.onemfive.core.client;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The flat, serialisable envelope carried across the {@link CoreClient} boundary.
 *
 * <p>Names no bus primitive: no {@code ra.common.Envelope}, no {@code Class}-keyed
 * channel, no polymorphic routing slip. A host (Remnant's {@code :app}, the
 * localhost RPC API a future {@code HttpCoreClient} speaks) sees only this shape.
 * See {@code DESIGN.md} §"The embedding contract (CoreClient)" - this class, the
 * {@link CoreClient} verb table, and {@link ProtocolHandle}/{@link CoreInbound} must
 * read identically here, in {@code 1m5-remnant/DESIGN.md}, and in
 * {@code 1m5-docs/architecture/README.md}.
 *
 * <p>{@link #slip} is the route: a front-to-back sequence of {@link Hop}s (front =
 * next hop), the opposite order of the {@code ra.common} routing slip's LIFO stack
 * ({@link MsgTranslator} reverses it). Each {@link Hop} pairs a channel (the
 * service) with an operation (the method on it) - the same split
 * {@code ra.common.route.Route} already makes; a channel is not addressable
 * without saying which of its methods to invoke, so the two never appear apart. A
 * protocol/transport channel only ever implements one operation
 * ({@code ProtocolService.OPERATION_SEND}, applied automatically by
 * {@link MsgTranslator} regardless of what a {@link Hop} says); a business or data
 * channel (e.g. {@code "Bitcoin"}) implements several, so a host addressing one
 * always sets the operation - there is no sensible default for it.
 * {@link #to(String, String)} is the common single-hop case: it replaces the whole
 * slip with one {@link Hop}.
 *
 * <p>Routing scalars are carried as reserved {@code x.*} headers rather than typed
 * fields: {@code x.sensitivity} (0-10), {@code x.url}, {@code x.serviceLevel},
 * {@code x.delayed} / {@code x.minDelayMs} / {@code x.maxDelayMs}, {@code x.copy} /
 * {@code x.minCopies} / {@code x.maxCopies}, {@code x.dest.{i2p,tor,bt,peerId}},
 * {@code x.relay.peerId}, {@code x.error.*}. Everything else in {@link #headers} is
 * an ordinary application header (e.g. a business-channel operation's own named
 * parameters) passed through unchanged to the underlying {@code Envelope}'s header
 * map - see {@link MsgTranslator}.
 */
public final class Msg {

    /** One hop: a channel (the service) and the operation (the method) on it. */
    public static final class Hop {
        private final String channel;
        private final String operation;

        public Hop(String channel, String operation) {
            this.channel = channel;
            this.operation = operation;
        }

        public String getChannel() { return channel; }
        public String getOperation() { return operation; }

        @Override
        public String toString() { return channel + ":" + operation; }
    }

    private String id;
    private String sender;
    private Map<String, String> headers = new LinkedHashMap<>();
    private byte[] payload;
    private Deque<Hop> slip = new ArrayDeque<>();
    private int attempts;

    public Msg() {}

    public String getId() { return id; }
    public Msg setId(String id) { this.id = id; return this; }

    public String getSender() { return sender; }
    public Msg setSender(String sender) { this.sender = sender; return this; }

    public Map<String, String> getHeaders() { return headers; }
    public Msg setHeaders(Map<String, String> headers) {
        this.headers = headers != null ? headers : new LinkedHashMap<>();
        return this;
    }
    public Msg header(String name, String value) { headers.put(name, value); return this; }
    public String header(String name) { return headers.get(name); }

    public byte[] getPayload() { return payload; }
    public Msg setPayload(byte[] payload) { this.payload = payload; return this; }

    public Deque<Hop> getSlip() { return slip; }
    public Msg setSlip(Deque<Hop> slip) {
        this.slip = slip != null ? slip : new ArrayDeque<>();
        return this;
    }

    /** Address a single channel/operation directly - the common case. Replaces the whole slip. */
    public Msg to(String channel, String operation) {
        this.slip = new ArrayDeque<>();
        this.slip.addLast(new Hop(channel, operation));
        return this;
    }

    /** {@link #to(String, String)} with no operation - only meaningful for a protocol/transport channel. */
    public Msg to(String channel) { return to(channel, null); }

    /** The next hop this Msg is addressed to, or {@code null} if the slip is empty. */
    public Hop nextHop() { return slip.peekFirst(); }

    public int getAttempts() { return attempts; }
    public Msg setAttempts(int attempts) { this.attempts = attempts; return this; }

    @Override
    public String toString() {
        return "Msg{id=" + id + ", slip=" + slip + ", sender=" + sender
                + ", headers=" + headers + ", payloadLen="
                + (payload != null ? payload.length : 0)
                + ", attempts=" + attempts + "}";
    }
}
