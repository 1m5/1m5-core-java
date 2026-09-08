package network.onemfive.core.routing;

import network.onemfive.core.ManCon;
import network.onemfive.core.ManConStatus;
import org.junit.Test;
import ra.common.network.Network;
import ra.common.network.NetworkPeer;

import java.util.EnumSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

/**
 * The pure escalation decision engine: ManCon &times; (web | P2P) network
 * selection, address-matched transport choice, cross-transport relay, and hold.
 */
public class EscalationRouterTest {

    private final EscalationRouter engine = new EscalationRouter();

    private static ManConStatus band(ManCon minRequired, ManCon maxAvailable) {
        ManConStatus s = new ManConStatus();
        s.setMinRequired(minRequired);
        s.setMaxAvailable(maxAvailable);
        return s;
    }

    private static Set<Network> ready(Network... nets) {
        Set<Network> s = EnumSet.noneOf(Network.class);
        for (Network n : nets) s.add(n);
        return s;
    }

    private static NetworkPeer peer(String fp, Network net) {
        NetworkPeer p = new NetworkPeer(net);
        p.setId(fp);
        return p;
    }

    // -- web requests ------------------------------------------------

    @Test
    public void webLowGoesDirectOverHttp() {
        RoutingDecision d = engine.decide(true, false, ManCon.LOW, null, null,
                band(ManCon.LOW, ManCon.LOW), ready(Network.HTTP), new PeerDirectory());
        assertEquals(RoutingDecision.Kind.DIRECT, d.kind);
        assertEquals(Network.HTTP, d.network);
    }

    @Test
    public void webHighPrefersTorThenI2p() {
        RoutingDecision tor = engine.decide(true, false, ManCon.HIGH, null, null,
                band(ManCon.HIGH, ManCon.VERYHIGH), ready(Network.Tor, Network.I2P), new PeerDirectory());
        assertEquals(Network.Tor, tor.network);

        RoutingDecision i2p = engine.decide(true, false, ManCon.HIGH, null, null,
                band(ManCon.HIGH, ManCon.VERYHIGH), ready(Network.I2P), new PeerDirectory());
        assertEquals(Network.I2P, i2p.network);
    }

    @Test
    public void localhostAlwaysDirectHttpRegardlessOfManCon() {
        RoutingDecision d = engine.decide(true, true, ManCon.NEO, null, null,
                band(ManCon.HIGH, ManCon.NONE), ready(Network.HTTP), new PeerDirectory());
        assertEquals(RoutingDecision.Kind.DIRECT, d.kind);
        assertEquals(Network.HTTP, d.network);
    }

    @Test
    public void localhostHoldsIfHttpNotReady() {
        RoutingDecision d = engine.decide(true, true, ManCon.LOW, null, null,
                band(ManCon.LOW, ManCon.VERYHIGH), ready(Network.I2P), new PeerDirectory());
        assertEquals(RoutingDecision.Kind.HOLD, d.kind);
    }

    @Test
    public void webRelaysThroughAPeerWhenNoOverlayIsReady() {
        PeerDirectory peers = new PeerDirectory();
        NetworkPeer relay = peer("relay-1", Network.Bluetooth);
        peers.putRelay(relay);

        RoutingDecision d = engine.decide(true, false, ManCon.EXTREME, null, null,
                band(ManCon.HIGH, ManCon.NEO), ready(Network.Bluetooth), peers);

        assertEquals(RoutingDecision.Kind.RELAYED, d.kind);
        assertEquals(Network.Bluetooth, d.network);
        assertSame(relay, d.relayPeer);
    }

    // -- peer-to-peer ---------------------------------------------

    @Test
    public void p2pAddressMatchedSelection() {
        PeerDirectory peers = new PeerDirectory();
        peers.put(peer("bob", Network.I2P));
        peers.put(peer("bob", Network.Tor));

        RoutingDecision d = engine.decide(false, false, ManCon.HIGH, "bob", null,
                band(ManCon.HIGH, ManCon.VERYHIGH), ready(Network.Tor), peers);

        assertEquals(RoutingDecision.Kind.DIRECT, d.kind);
        assertEquals("only Tor is ready, and bob has a Tor address", Network.Tor, d.network);
        assertEquals("bob", d.destination.getId());
    }

    @Test
    public void p2pCrossTransportRelayWhenPeersNetworkIsBlocked() {
        PeerDirectory peers = new PeerDirectory();
        peers.put(peer("bob", Network.I2P));          // bob is only on I2P
        peers.putRelay(peer("relay-1", Network.Tor)); // a relay is reachable on Tor

        RoutingDecision d = engine.decide(false, false, ManCon.HIGH, "bob", null,
                band(ManCon.HIGH, ManCon.HIGH), ready(Network.Tor), peers); // I2P down, Tor up

        assertEquals(RoutingDecision.Kind.RELAYED, d.kind);
        assertEquals(Network.Tor, d.network);
        assertEquals("relay-1", d.relayPeer.getId());
        assertEquals("bob", d.destination.getId());
    }

    @Test
    public void p2pHoldsWhenNothingReachable() {
        PeerDirectory peers = new PeerDirectory();
        peers.put(peer("bob", Network.I2P));

        RoutingDecision d = engine.decide(false, false, ManCon.HIGH, "bob", null,
                band(ManCon.HIGH, ManCon.HIGH), ready(Network.Tor), peers); // no relay, I2P down

        assertEquals(RoutingDecision.Kind.HOLD, d.kind);
    }

    @Test
    public void veryHighDropsTorEvenIfItIsReady() {
        PeerDirectory peers = new PeerDirectory();
        peers.put(peer("bob", Network.Tor)); // bob only on Tor

        RoutingDecision d = engine.decide(false, false, ManCon.VERYHIGH, "bob", null,
                band(ManCon.HIGH, ManCon.VERYHIGH), ready(Network.Tor), peers);

        assertEquals("Tor is not acceptable at VERYHIGH", RoutingDecision.Kind.HOLD, d.kind);
    }

    @Test
    public void undirectedP2pTakesFirstReadyAcceptableNetwork() {
        RoutingDecision d = engine.decide(false, false, ManCon.HIGH, null, null,
                band(ManCon.HIGH, ManCon.VERYHIGH), ready(Network.I2P), new PeerDirectory());
        assertEquals(RoutingDecision.Kind.DIRECT, d.kind);
        assertEquals(Network.I2P, d.network);
        assertNull(d.destination);
    }

    // -- ManCon band interaction --------------------------------

    @Test
    public void requestMoreSevereThanAvailableIsServedAtTheCeiling() {
        PeerDirectory peers = new PeerDirectory();
        peers.put(peer("bob", Network.I2P));

        // operator floor HIGH, only I2P up (VERYHIGH ceiling), envelope asks EXTREME
        RoutingDecision d = engine.decide(false, false, ManCon.EXTREME, "bob", null,
                band(ManCon.HIGH, ManCon.VERYHIGH), ready(Network.I2P), peers);

        assertEquals(RoutingDecision.Kind.DIRECT, d.kind);
        assertEquals(ManCon.VERYHIGH, d.effectiveManCon);
    }

    @Test
    public void operatorFloorThatCannotBeMetHolds() {
        PeerDirectory peers = new PeerDirectory();
        peers.put(peer("bob", Network.I2P));

        // operator demands NEO, only I2P up -> cannot meet the floor
        RoutingDecision d = engine.decide(false, false, ManCon.NEO, "bob", null,
                band(ManCon.NEO, ManCon.VERYHIGH), ready(Network.I2P), peers);

        assertEquals(RoutingDecision.Kind.HOLD, d.kind);
    }
}
