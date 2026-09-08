package network.onemfive.core.routing;

import ra.common.network.Network;
import ra.common.network.NetworkPeer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory directory of known peers and which networks each is reachable on.
 * The router uses it to
 * <ul>
 *   <li>find a peer's address on a given network (address-matched selection), and</li>
 *   <li>find a relay peer on a reachable network when the peer's own network is
 *       blocked (cross-transport relay).</li>
 * </ul>
 *
 * <p>A physical peer is one identity (a DID fingerprint) reachable at several
 * {@code (network, address)} pairs; each pair is one {@link NetworkPeer}. So this
 * indexes {@code fingerprint -> network -> NetworkPeer}.
 *
 * <p><b>Skeleton scope:</b> a plain map with no liveness or reliability scoring.
 * The real implementation reuses {@code ra.networkmanager.InMemoryPeerDB} /
 * {@code P2PRelationship} (ack latency, reliability, relationship graph) once that
 * dependency is added - see TODO.md.
 */
public final class PeerDirectory {

    // fingerprint -> (network -> peer address on that network)
    private final Map<String, Map<Network, NetworkPeer>> byFingerprint = new ConcurrentHashMap<>();
    // fingerprints of peers willing to relay for others
    private final Set<String> relays = Collections.newSetFromMap(new ConcurrentHashMap<>());

    public void put(NetworkPeer peer) {
        if (peer == null || peer.getId() == null || peer.getNetwork() == null) return;
        byFingerprint
                .computeIfAbsent(peer.getId(), k -> new EnumMap<>(Network.class))
                .put(peer.getNetwork(), peer);
    }

    /** Register {@code peer} and mark its fingerprint as relay-capable. */
    public void putRelay(NetworkPeer peer) {
        put(peer);
        if (peer != null && peer.getId() != null) relays.add(peer.getId());
    }

    public void markRelay(String fingerprint) {
        if (fingerprint != null) relays.add(fingerprint);
    }

    /** That peer's address on {@code network}, or null if it has none. */
    public NetworkPeer address(String fingerprint, Network network) {
        if (fingerprint == null || network == null) return null;
        Map<Network, NetworkPeer> m = byFingerprint.get(fingerprint);
        return m == null ? null : m.get(network);
    }

    /** Networks {@code fingerprint} is known to be reachable on. */
    public Set<Network> networksFor(String fingerprint) {
        Map<Network, NetworkPeer> m = byFingerprint.get(fingerprint);
        return m == null ? Collections.emptySet() : m.keySet();
    }

    public boolean isKnown(String fingerprint) {
        return byFingerprint.containsKey(fingerprint);
    }

    /** Relay-capable peers reachable on {@code network} (excluding {@code exclude}). */
    public List<NetworkPeer> relaysOn(Network network, String exclude) {
        List<NetworkPeer> out = new ArrayList<>();
        for (String fp : relays) {
            if (fp.equals(exclude)) continue;
            NetworkPeer p = address(fp, network);
            if (p != null) out.add(p);
        }
        return out;
    }

    /** Any peer (relay or not) reachable on {@code network}. */
    public List<NetworkPeer> byNetwork(Network network) {
        List<NetworkPeer> out = new ArrayList<>();
        for (Map<Network, NetworkPeer> m : byFingerprint.values()) {
            NetworkPeer p = m.get(network);
            if (p != null) out.add(p);
        }
        return out;
    }

    public int size() {
        return byFingerprint.size();
    }

    public void clear() {
        byFingerprint.clear();
        relays.clear();
    }
}
