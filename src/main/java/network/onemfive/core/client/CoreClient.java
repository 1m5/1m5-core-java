package network.onemfive.core.client;

import java.util.List;
import java.util.Map;

/**
 * The host&harr;core embedding contract. A narrow facade that names no bus
 * primitive - a flat {@link Msg}, string channel names, callback-shaped transport
 * registration - so a host (Remnant's {@code :app}, a future {@code HttpCoreClient}
 * on the desktop RPC path) never sees {@code ra.common.Envelope}, the service-bus
 * API, or FQCN-keyed channels.
 *
 * <p>Two implementations: {@link EmbeddedCoreClient} sits over an in-process
 * {@code Core.get()} (this module); a future {@code HttpCoreClient} will talk to a
 * running node over its localhost RPC API (ADR-0003), using this same {@link Msg}
 * shape as the wire encoding.
 *
 * <p>See {@code DESIGN.md} §"The embedding contract (CoreClient)". This verb table
 * must read identically there, in {@code 1m5-remnant/DESIGN.md}, and in
 * {@code 1m5-docs/architecture/README.md}.
 */
public interface CoreClient {

    /** Boot the core with a flat string config. */
    boolean start(Map<String, String> config);

    /** Shut the core down. */
    void stop();

    /** Fire-and-forget outbound. */
    boolean send(Msg msg);

    /** Outbound with a completion callback. */
    boolean send(Msg msg, ReplyHandler callback);

    /**
     * Register a transport the host owns. Returns the sink the core calls for
     * every inbound payload that transport receives.
     */
    CoreInbound registerProtocol(ProtocolHandle handle);

    /**
     * Register the app's own inbox for a business channel (e.g. {@code
     * "messaging"}): {@code handler} is called whenever a {@link Msg}'s slip
     * terminates at {@code channel} - this app is the final destination, not a
     * relay hop. The counterpart to {@link #registerProtocol}: there, the host
     * hands the core inbound bytes; here, the core hands the host a finished
     * message. Returns whether registration completed.
     */
    boolean registerChannel(String channel, CoreInbound handler);

    /** Block until the named channels are ready, or the timeout elapses. No channels named: return immediately. */
    boolean awaitReady(long timeoutMs, String... channels);

    /** Which transports report ready right now. */
    List<TransportStatus> readyTransports();

    /** Node identity summary (public id only). */
    IdentityStatus identityStatus();

    /** BIP-340 sign with the node key. Throws if the node has no usable secret. */
    byte[] signAsNode(byte[] canonicalEvent);

    /**
     * Verify a BIP-340 signature against the SHA-256 of {@code canonicalBytes},
     * using someone else's public key hex - not tied to this node's own
     * identity, unlike {@link #signAsNode}. Lets a host check who really sent
     * something (a message, an attestation, ...) without ever importing
     * {@code did-java}'s {@code Bip340} itself. False on any malformed input
     * rather than throwing, since a bad signature/key from a peer is an
     * ordinary outcome to check for, not an exceptional one.
     */
    boolean verify(byte[] signature, byte[] canonicalBytes, String publicKeyHex);
}
