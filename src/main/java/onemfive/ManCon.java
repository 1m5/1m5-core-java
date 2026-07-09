package onemfive;

public enum ManCon {
    NEO, // MANCON 0
    EXTREME, // MANCON 1
    VERYHIGH, // MANCON 2
    HIGH, // MANCON 3
    MEDIUM, // MANCON 4
    LOW, // MANCON 5
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

    public static ManCon fromSensitivity(Integer sensitivity) {
        if(sensitivity >= 10) return NEO;
        if(sensitivity >= 8) return EXTREME;
        if(sensitivity >= 6) return VERYHIGH;
        if(sensitivity >= 4) return HIGH;
        if(sensitivity >= 2) return MEDIUM;
        if(sensitivity > 0) return LOW;
        return NONE;
    }

    public static Integer toSensitivity(ManCon manCon) {
        switch(manCon) {
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
