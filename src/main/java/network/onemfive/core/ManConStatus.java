package network.onemfive.core;

/**
 * The three ManCon parameters the router weighs for every routed envelope:
 *
 *   None    HTTP    Tor     I2P     [Non-Internet: BT Mesh, WiFi, Satellite, FS Radio, LiFi]
 *   -----------------------------------------------------------------------------------------
 *                    |       |      |
 *                   MR      MA      MS
 *
 * <ul>
 *   <li><b>Min Required</b> - set by the end user / config: the lowest acceptable
 *       ManCon to use for communications.</li>
 *   <li><b>Max Available</b> - updated in real time from what the connected
 *       {@link network.onemfive.core.service.ProtocolService}s and discovered peers
 *       can currently support.</li>
 *   <li><b>Max Supported</b> - the ceiling implied by which ProtocolServices were
 *       registered at all.</li>
 * </ul>
 *
 * Unlike the legacy {@code onemfive.ManConStatus} (global mutable statics), this is
 * an instance owned by {@link Core} and read by the router.
 */
public final class ManConStatus {

    private volatile ManCon minRequired = ManCon.HIGH;
    private volatile ManCon maxAvailable = ManCon.NONE;
    private volatile ManCon maxSupported = ManCon.EXTREME;

    public ManCon getMinRequired() { return minRequired; }
    public void setMinRequired(ManCon m) { this.minRequired = m; }

    public ManCon getMaxAvailable() { return maxAvailable; }
    public void setMaxAvailable(ManCon m) { this.maxAvailable = m; }

    public ManCon getMaxSupported() { return maxSupported; }
    public void setMaxSupported(ManCon m) { this.maxSupported = m; }

    /**
     * Clamp a requested ManCon into the [maxAvailable, minRequired] band.
     * (Ordinals run most-severe-first, so "more severe than maxAvailable" means a
     * smaller ordinal than maxAvailable.)
     */
    public ManCon select(ManCon requested) {
        if (requested.ordinal() < maxAvailable.ordinal()) return maxAvailable;
        if (requested.ordinal() > minRequired.ordinal()) return minRequired;
        return requested;
    }
}
