package network.onemfive.core.routing;

import ra.common.network.Network;
import ra.common.network.NetworkPeer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Minimal in-memory directory of known peers and which networks each is
 * reachable on. The router uses it to find relay peers when the desired network
 * is blocked.
 *
 * <p><b>Skeleton scope:</b> a plain map. The real implementation reuses
 * {@code ra.networkmanager.InMemoryPeerDB} / {@code P2PRelationship} (ack latency,
 * reliability scoring, relationship graph) once that dependency is added - see
 * TODO.md.
 */
public final class PeerDirectory {

    // fingerprint -> peer
    private final Map<String, NetworkPeer> peers = new ConcurrentHashMap<>();

    public void put(NetworkPeer peer) {
        if (peer != null && peer.getId() != null) {
            peers.put(peer.getId(), peer);
        }
    }

    public NetworkPeer get(String fingerprint) {
        return peers.get(fingerprint);
    }

    public int size() {
        return peers.size();
    }

    /** Peers known to be reachable on the given network. */
    public List<NetworkPeer> byNetwork(Network network) {
        List<NetworkPeer> out = new ArrayList<>();
        for (NetworkPeer p : peers.values()) {
            if (p.getNetwork() == network) out.add(p);
        }
        return out;
    }
}
