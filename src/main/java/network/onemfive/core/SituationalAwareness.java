package network.onemfive.core;

import ra.common.network.Network;

import java.util.ArrayList;
import java.util.List;

/**
 * A snapshot of everything the router considered for one envelope. Kept as a
 * plain data holder so routing decisions can be logged and explained (see
 * {@code DESIGN.md} - "Route Selection Should Be Explicit").
 */
public class SituationalAwareness {

    // -- inputs -------------------------------------------------------
    public boolean isWebRequest;
    public boolean localhostUrl;
    public int envelopeSensitivity;
    public ManCon envelopeManCon;
    public String destinationFingerprint;

    // -- the ManCon band at decision time --------------------------
    public ManCon minRequired;
    public ManCon maxAvailable;
    public ManCon selectedManCon;
    public boolean meetsFloor;
    public boolean withinMaxAvailable;

    // -- the decision ---------------------------------------------
    public List<Network> acceptableNetworks = new ArrayList<>();
    public List<Network> readyNetworks = new ArrayList<>();
    public String decision;          // DIRECT | RELAYED | HOLD | DROP
    public Network chosenNetwork;
    public Network relayNetwork;
    public String relayPeerFingerprint;
    public int attempt;
    public String reason;

    @Override
    public String toString() {
        return "SituationalAwareness{" +
                "web=" + isWebRequest +
                (localhostUrl ? " localhost" : "") +
                ", sensitivity=" + envelopeSensitivity +
                ", manCon=" + envelopeManCon +
                ", band=[max " + maxAvailable + " .. min " + minRequired + "]" +
                ", selected=" + selectedManCon +
                (meetsFloor ? "" : " (BELOW FLOOR)") +
                ", dest=" + destinationFingerprint +
                ", acceptable=" + acceptableNetworks +
                ", ready=" + readyNetworks +
                ", decision=" + decision +
                (chosenNetwork != null ? " via " + chosenNetwork : "") +
                (relayNetwork != null ? " relay " + relayNetwork + "/" + relayPeerFingerprint : "") +
                (attempt > 0 ? " attempt=" + attempt : "") +
                ", reason=" + reason +
                '}';
    }
}
