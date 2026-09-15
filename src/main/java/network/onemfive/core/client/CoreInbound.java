package network.onemfive.core.client;

/**
 * The sink a {@link CoreClient#registerProtocol(ProtocolHandle)} caller gets back.
 * The host calls {@link #accept(Msg)} for every inbound payload its transport
 * receives; the core takes it from there.
 *
 * <p><b>Known gap (tracked in {@code TODO.md}):</b> the current ~8-verb
 * {@link CoreClient} contract has no verb for an app-layer host to register its own
 * inbound listener, so {@link network.onemfive.core.client.EmbeddedCoreClient}'s
 * implementation currently only feeds accepted messages onto the bus as a fresh
 * envelope (see {@code HandleBackedProtocolService.inbound()}) - there is not yet a
 * defined path for that envelope to surface back out to an embedding app. Resolve
 * before wiring a real transport end-to-end.
 */
@FunctionalInterface
public interface CoreInbound {
    void accept(Msg msg);
}
