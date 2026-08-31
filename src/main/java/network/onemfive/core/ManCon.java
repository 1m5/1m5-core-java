package network.onemfive.core;

/**
 * Maneuvering Condition - the level of maneuvering likely required to resist
 * censorship for a given communication. Set by the end user (or configuration)
 * as a baseline; the effective level is negotiated at routing time against what
 * the network can currently support.
 *
 * Ordinal ordering runs from most severe (NEO = 0) to least (LOW = 5); NONE and
 * UNKNOWN sit outside the scale.
 */
public enum ManCon {
    NEO,       // MANCON 0 - whistleblower / life-threatening information
    EXTREME,   // MANCON 1 - internet blocked for the end user
    VERYHIGH,  // MANCON 2 - overlays actively attacked, self-censorship
    HIGH,      // MANCON 3 - Tor/VPN beginning to be blocked (1M5 default)
    MEDIUM,    // MANCON 4 - normal state censorship of public web
    LOW,       // MANCON 5 - open SSL communications, no expected interference
    NONE,
    UNKNOWN;

    public static ManCon fromOrdinal(int i) {
        switch (i) {
            case 0: return NEO;
            case 1: return EXTREME;
            case 2: return VERYHIGH;
            case 3: return HIGH;
            case 4: return MEDIUM;
            case 5: return LOW;
            default: return NONE;
        }
    }

    /** Map an {@link ra.common.Envelope} sensitivity (0-10) to a ManCon level. */
    public static ManCon fromSensitivity(Integer sensitivity) {
        if (sensitivity == null) return NONE;
        if (sensitivity >= 10) return NEO;
        if (sensitivity >= 8) return EXTREME;
        if (sensitivity >= 6) return VERYHIGH;
        if (sensitivity >= 4) return HIGH;
        if (sensitivity >= 2) return MEDIUM;
        if (sensitivity > 0) return LOW;
        return NONE;
    }

    public static Integer toSensitivity(ManCon manCon) {
        switch (manCon) {
            case NEO: return 10;
            case EXTREME: return 8;
            case VERYHIGH: return 6;
            case HIGH: return 4;
            case MEDIUM: return 2;
            case LOW: return 1;
            default: return 0;
        }
    }
}
