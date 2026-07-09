package onemfive;

public final class ManConStatus {

    // Max Supported ManCon is the highest supported ManCon determination by looking at what Networks were registered and are active.
    // Current default setting of EXTREME is based on registering TOR, I2P, and Bluetooth by default.
    public static ManCon MAX_SUPPORTED_MANCON = ManCon.EXTREME;

    // Max Available ManCon is the current level of ManCon that can be supported
    // changing in real-time based on network connectivity and peer discovery.
    // Initial setting is NONE until sensors come on line, connect, and discover peers.
    public static ManCon MAX_AVAILABLE_MANCON = ManCon.NONE;

    // Min Required ManCon is set by end users or system admins for daemons to indicate the minimum ManCon to use for current communications.
    public static ManCon MIN_REQUIRED_MANCON = ManCon.HIGH;

}
