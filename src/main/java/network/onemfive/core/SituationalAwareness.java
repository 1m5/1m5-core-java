package network.onemfive.core;

import ra.common.network.Network;

/**
 * A snapshot of everything the router considered for one envelope. Kept as a
 * plain data holder so routing decisions can be logged and explained (see
 * {@code DESIGN.md} - "Route Selection Should Be Explicit").
 */
public class SituationalAwareness {
    public Network desiredNetwork = null;
    public boolean desiredNetworkConnected = false;
    public boolean isWebRequest;
    public int envelopeSensitivity;
    public ManCon envelopeManCon;
    public boolean withinMaxAvailable;
    public boolean withinMinRequired;
    public ManCon selectedManCon;

    @Override
    public String toString() {
        return "SituationalAwareness{" +
                "desiredNetwork=" + desiredNetwork +
                ", desiredNetworkConnected=" + desiredNetworkConnected +
                ", isWebRequest=" + isWebRequest +
                ", envelopeSensitivity=" + envelopeSensitivity +
                ", envelopeManCon=" + envelopeManCon +
                ", withinMaxAvailable=" + withinMaxAvailable +
                ", withinMinRequired=" + withinMinRequired +
                ", selectedManCon=" + selectedManCon +
                '}';
    }
}
