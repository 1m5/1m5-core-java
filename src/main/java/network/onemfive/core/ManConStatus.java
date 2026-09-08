package network.onemfive.core;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;

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

    private static final Logger LOG = Logger.getLogger(ManConStatus.class.getName());

    /** Bundled RSF-Press-Freedom-Index → ManCon table (see {@link #defaultFor}). */
    private static final String JURISDICTIONS_RESOURCE = "/jurisdictions-levels.txt";
    private static volatile Map<String, ManCon> jurisdictions;

    private volatile ManCon minRequired = ManCon.HIGH;
    private volatile ManCon maxAvailable = ManCon.NONE;
    private volatile ManCon maxSupported = ManCon.EXTREME;

    public ManCon getMinRequired() { return minRequired; }
    public void setMinRequired(ManCon m) { this.minRequired = m; }

    /**
     * Seed {@link #minRequired} (the operator/user floor) from the user's
     * jurisdiction — see {@link #defaultFor(String)}.
     */
    public void applyJurisdiction(String iso2) {
        setMinRequired(defaultFor(iso2));
    }

    /**
     * The recommended default ManCon floor for a user in the given jurisdiction,
     * from the bundled {@code jurisdictions-levels.txt} (RSF World Press Freedom
     * Index band → ManCon: Good→LOW, Satisfactory→MEDIUM, Problematic→HIGH,
     * Difficult→VERYHIGH, Very serious→EXTREME). Falls back to the file's
     * {@code *} line, then to {@link ManCon#HIGH}.
     *
     * @param iso2 an ISO 3166-1 alpha-2 country code, case-insensitive; {@code null}
     *             or blank returns the fallback. The user→jurisdiction lookup
     *             (GeoIP, SIM MCC, locale, an explicit setting) is the host's job.
     */
    public static ManCon defaultFor(String iso2) {
        Map<String, ManCon> m = jurisdictions();
        ManCon fallback = m.getOrDefault("*", ManCon.HIGH);
        if (iso2 == null) return fallback;
        return m.getOrDefault(iso2.trim().toUpperCase(Locale.ROOT), fallback);
    }

    private static Map<String, ManCon> jurisdictions() {
        Map<String, ManCon> m = jurisdictions;
        if (m != null) return m;
        synchronized (ManConStatus.class) {
            if (jurisdictions != null) return jurisdictions;
            m = new HashMap<>();
            try (InputStream in = ManConStatus.class.getResourceAsStream(JURISDICTIONS_RESOURCE)) {
                if (in == null) {
                    LOG.warning(JURISDICTIONS_RESOURCE + " not on the classpath - defaultFor() returns HIGH");
                } else {
                    BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
                    String line;
                    while ((line = r.readLine()) != null) {
                        line = line.trim();
                        if (line.isEmpty() || line.charAt(0) == '#') continue;
                        String[] tok = line.split("\\s+", 3);
                        if (tok.length < 2) continue;
                        try {
                            m.put(tok[0], ManCon.valueOf(tok[1]));
                        } catch (IllegalArgumentException ex) {
                            LOG.warning("jurisdictions-levels.txt: unknown ManCon '" + tok[1] + "'");
                        }
                    }
                }
            } catch (Exception e) {
                LOG.warning("jurisdictions-levels.txt: " + e.getMessage());
            }
            jurisdictions = m;
            return m;
        }
    }

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
