package network.onemfive.core.client;

/**
 * A one-way sink for a finished {@link Msg}, used in both directions across the
 * {@link CoreClient} boundary:
 *
 * <ul>
 *   <li>{@link CoreClient#registerProtocol(ProtocolHandle)} returns one: the host
 *       calls {@link #accept(Msg)} for every inbound payload its transport
 *       receives, and the core takes it from there.</li>
 *   <li>{@link CoreClient#registerChannel(String, CoreInbound)} takes one: the
 *       core calls it when a {@link Msg}'s slip terminates at the app's own
 *       business channel - the app is the final destination, not a relay hop.</li>
 * </ul>
 *
 * <p>Reusing this one type for both directions (rather than adding a second,
 * near-identical functional interface) closed what {@code TODO.md} §"Embedding
 * contract" used to track as the "known gap": a host-supplied
 * {@link ProtocolHandle}'s inbound bytes had nowhere to go once re-injected onto
 * the bus - {@code registerChannel} is what a business channel like {@code
 * "messaging"} registers to actually receive them.
 */
@FunctionalInterface
public interface CoreInbound {
    void accept(Msg msg);
}
