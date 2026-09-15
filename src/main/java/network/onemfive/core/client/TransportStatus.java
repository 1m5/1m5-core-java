package network.onemfive.core.client;

/**
 * A transport's status as seen across the {@link CoreClient} boundary - a plain
 * value, not {@code ra.common.network.NetworkStatus} (that enum is a bus/transport
 * implementation detail; the host only needs "which channel" and "can it carry a
 * message right now").
 */
public final class TransportStatus {

    private final String name;
    private final boolean ready;
    private final String state;
    private final String localAddress;

    public TransportStatus(String name, boolean ready, String state) {
        this(name, ready, state, null);
    }

    public TransportStatus(String name, boolean ready, String state, String localAddress) {
        this.name = name;
        this.ready = ready;
        this.state = state;
        this.localAddress = localAddress;
    }

    /** Stable channel name, e.g. {@code "I2P"} - matches {@link ProtocolHandle#name()}. */
    public String getName() { return name; }

    /** Ready to carry a message right now. */
    public boolean isReady() { return ready; }

    /** Free-form, human-readable state for diagnostics (mirrors the underlying network status). */
    public String getState() { return state; }

    /** This device's own address on this transport - what a contact needs to reach it. Null if none yet. */
    public String getLocalAddress() { return localAddress; }

    @Override
    public String toString() {
        return "TransportStatus{" + name + ", ready=" + ready + ", state=" + state
                + ", localAddress=" + localAddress + "}";
    }
}
