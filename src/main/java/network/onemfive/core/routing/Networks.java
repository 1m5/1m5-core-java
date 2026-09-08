package network.onemfive.core.routing;

import ra.common.network.Network;

import java.util.EnumSet;
import java.util.Set;

/**
 * Classification of {@link Network}s the router uses when deciding which
 * transports are acceptable for a given {@link network.onemfive.core.ManCon}.
 *
 * <pre>
 *   None(clearnet)   HTTP    Tor     I2P    | Non-Internet: Bluetooth, WiFi, Satellite, FSRadio, LiFi
 *   -----------------------------------------------------------------------------------------------
 *                                           |
 *   less censorship-resistant  ------------->|  internet-independent
 * </pre>
 */
public final class Networks {

    private Networks() {}

    /** Internet transports that provide no meaningful censorship resistance on their own. */
    public static final Set<Network> CLEARNET =
            EnumSet.of(Network.HTTP, Network.Card, Network.NFC);

    /** Internet overlays: work while the internet is reachable, resist observation/blocking. */
    public static final Set<Network> INTERNET_OVERLAY =
            EnumSet.of(Network.Tor, Network.I2P);

    /** Transports that do not depend on the public internet at all. */
    public static final Set<Network> NON_INTERNET =
            EnumSet.of(Network.Bluetooth, Network.WiFi, Network.Satellite, Network.FSRadio, Network.LiFi);

    public static boolean isClearnet(Network n) { return CLEARNET.contains(n); }

    public static boolean isInternetOverlay(Network n) { return INTERNET_OVERLAY.contains(n); }

    public static boolean isNonInternet(Network n) { return NON_INTERNET.contains(n); }

    /** Needs the public internet to function (clearnet or an internet overlay). */
    public static boolean needsInternet(Network n) {
        return isClearnet(n) || isInternetOverlay(n);
    }
}
