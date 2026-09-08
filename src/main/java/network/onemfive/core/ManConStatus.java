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

    /**
     * Set the most severe ManCon the connected transports can currently deliver.
     * Returns true if the value changed - the router fires
     * {@link ManConStatusListener}s on a true return.
     *
     * <p>This is what the router keeps current (from
     * {@code RoutingService.refreshAvailability()}); before that wiring it stayed
     * at {@link ManCon#NONE} and {@link #select} collapsed every request to NONE.
     */
    public boolean setMaxAvailable(ManCon m) {
        if (m == null || m == maxAvailable) return false;
        this.maxAvailable = m;
        return true;
    }

    public ManCon getMaxSupported() { return maxSupported; }
    public void setMaxSupported(ManCon m) { this.maxSupported = m; }

    /**
     * Clamp a requested ManCon into the achievable band. Ordinals run
     * most-severe-first (NEO = 0), so:
     * <ul>
     *   <li>the result is never <em>less</em> severe than {@link #minRequired}
     *       (the floor the operator set) - a low-sensitivity request is raised;</li>
     *   <li>the result is never <em>more</em> severe than {@link #maxAvailable}
     *       (what the transports can deliver) - an over-ambitious request is
     *       clamped down.</li>
     * </ul>
     * When {@code maxAvailable} is itself less severe than {@code minRequired}
     * (the transports cannot even meet the floor) the band is inverted and this
     * returns {@code maxAvailable}; callers detect that with
     * {@link #meetsFloor(ManCon)} and hold the envelope rather than downgrade it.
     */
    public ManCon select(ManCon requested) {
        int floor = minRequired.ordinal();      // least severe acceptable (larger ordinal)
        int ceil = maxAvailable.ordinal();      // most severe achievable   (smaller ordinal)
        int r = requested.ordinal();
        if (r > floor) r = floor;
        if (r < ceil) r = ceil;
        return ManCon.fromOrdinal(r);
    }

    /** True if {@code level} is at least as severe as the operator's floor. */
    public boolean meetsFloor(ManCon level) {
        return level.ordinal() <= minRequired.ordinal();
    }
}
