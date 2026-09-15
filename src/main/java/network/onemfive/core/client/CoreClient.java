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

    /** Block until the named channels are ready, or the timeout elapses. No channels named: return immediately. */
    boolean awaitReady(long timeoutMs, String... channels);

    /** Which transports report ready right now. */
    List<TransportStatus> readyTransports();

    /** Node identity summary (public id only). */
    IdentityStatus identityStatus();

    /** BIP-340 sign with the node key. Throws if the node has no usable secret. */
    byte[] signAsNode(byte[] canonicalEvent);
}
