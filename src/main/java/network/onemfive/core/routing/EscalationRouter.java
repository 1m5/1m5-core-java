package network.onemfive.core.routing;

import network.onemfive.core.ManCon;
import network.onemfive.core.ManConStatus;
import ra.common.network.Network;
import ra.common.network.NetworkPeer;

import java.util.List;
import java.util.Set;

/**
 * The 1M5 escalation router as a <b>pure decision function</b>: given the facts
 * about an envelope and a snapshot of the network, it returns a
 * {@link RoutingDecision}. No bus, no threads, no I/O - so every branch is unit
 * testable.
 *
 * <p>{@link RoutingService} owns the bus glue (assembling the inputs, applying
 * the decision, holding + retrying). The logic here is the ladder ported from
 * {@code onemfive.routing.CRNetworkManagerService} and Remnant's
 * {@code RouterService.Sender}:
 *
 * <ol>
 *   <li>clamp the requested ManCon into the achievable band
 *       ({@link ManConStatus#select});</li>
 *   <li>if the clamp cannot meet the operator's floor &rarr; <b>HOLD</b>;</li>
 *   <li>build the acceptable-network list for the level
 *       ({@link ManConNetworks#acceptable});</li>
 *   <li><b>direct</b>: first acceptable network that is ready <i>and</i> that the
 *       destination has an address on (localhost and undirected traffic take the
 *       first ready acceptable network);</li>
 *   <li><b>relay</b>: the destination has an address on an acceptable network
 *       that is not ready, but a relay peer is reachable on one that is
 *       &rarr; <b>RELAYED</b>;</li>
 *   <li>nothing &rarr; <b>HOLD</b>.</li>
 * </ol>
 */
public final class EscalationRouter {

    /**
     * @param webRequest           envelope carries a URL
     * @param localhostUrl         the URL targets 127.0.0.1 / localhost
     * @param requestedManCon      from the envelope's sensitivity
     * @param destinationFingerprint peer DID fingerprint, or null for web / undirected
     * @param explicitDestination  a destination peer already named on the slip, or null
     * @param manConStatus         the live band
     * @param readyNetworks        networks whose protocol adapter reports ready
     * @param peers                the peer directory
     */
    public RoutingDecision decide(boolean webRequest,
                                  boolean localhostUrl,
                                  ManCon requestedManCon,
                                  String destinationFingerprint,
                                  NetworkPeer explicitDestination,
                                  ManConStatus manConStatus,
                                  Set<Network> readyNetworks,
                                  PeerDirectory peers) {

        ManCon selected = manConStatus.select(requestedManCon);

        // localhost is always a direct HTTP call, regardless of ManCon.
        if (localhostUrl) {
            if (readyNetworks.contains(Network.HTTP)) {
                return RoutingDecision.direct(Network.HTTP, null, selected,
                        java.util.Collections.singletonList(Network.HTTP), "localhost URL");
            }
            return RoutingDecision.hold(selected, java.util.Collections.singletonList(Network.HTTP),
                    "localhost URL but HTTP adapter not ready");
        }

        if (!manConStatus.meetsFloor(selected)) {
            return RoutingDecision.hold(selected, java.util.Collections.emptyList(),
                    "connected transports cannot meet the ManCon floor "
                            + manConStatus.getMinRequired() + " (maxAvailable="
                            + manConStatus.getMaxAvailable() + ")");
        }

        List<Network> acceptable = ManConNetworks.acceptable(selected, webRequest);
        if (acceptable.isEmpty()) {
            return RoutingDecision.hold(selected, acceptable, "no acceptable networks for " + selected);
        }

        boolean undirected = !webRequest && destinationFingerprint == null && explicitDestination == null;

        // 1. Direct.
        for (Network net : acceptable) {
            if (!readyNetworks.contains(net)) continue;

            if (webRequest) {
                // Only a network that can reach the internet can serve a URL directly;
                // over a non-internet transport a web request must be relayed.
                if (Networks.needsInternet(net)) {
                    return RoutingDecision.direct(net, null, selected, acceptable,
                            "web request over ready " + net);
                }
                continue;
            }
            if (undirected) {
                return RoutingDecision.direct(net, null, selected, acceptable,
                        "undirected over ready " + net);
            }
            NetworkPeer dest = destinationOn(net, destinationFingerprint, explicitDestination, peers);
            if (dest != null) {
                return RoutingDecision.direct(net, dest, selected, acceptable,
                        "destination reachable on ready " + net);
            }
        }

        // 2. Relay: destination has an address on an acceptable-but-not-ready network,
        //    and a relay peer is reachable on an acceptable network that IS ready.
        for (Network readyNet : acceptable) {
            if (!readyNetworks.contains(readyNet)) continue;

            if (webRequest) {
                // A peer with internet access fetches the URL for us.
                List<NetworkPeer> relays = peers.relaysOn(readyNet, null);
                if (!relays.isEmpty()) {
                    return RoutingDecision.relayed(readyNet, relays.get(0), null, selected, acceptable,
                            "web request relayed via a peer on ready " + readyNet);
                }
                continue;
            }

            for (Network blockedNet : acceptable) {
                if (blockedNet == readyNet || readyNetworks.contains(blockedNet)) continue;
                NetworkPeer dest = destinationOn(blockedNet, destinationFingerprint, explicitDestination, peers);
                if (dest == null) continue;
                List<NetworkPeer> relays = peers.relaysOn(readyNet, destinationFingerprint);
                if (!relays.isEmpty()) {
                    return RoutingDecision.relayed(readyNet, relays.get(0), dest, selected, acceptable,
                            "destination on blocked " + blockedNet + "; relaying via peer on ready " + readyNet);
                }
            }
        }

        // 3. Nothing.
        String why = undirected || webRequest
                ? "no acceptable network is ready"
                : (peers.isKnown(destinationFingerprint)
                    ? "destination known but neither its networks nor a relay network is ready"
                    : "destination " + destinationFingerprint + " not in the peer directory");
        return RoutingDecision.hold(selected, acceptable, why);
    }

    private static NetworkPeer destinationOn(Network net, String fingerprint,
                                             NetworkPeer explicit, PeerDirectory peers) {
        if (explicit != null && explicit.getNetwork() == net) return explicit;
        return peers.address(fingerprint, net);
    }
}
