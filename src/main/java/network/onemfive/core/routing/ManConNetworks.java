package network.onemfive.core.routing;

import network.onemfive.core.ManCon;
import ra.common.network.Network;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The ManCon &times; (web request | peer-to-peer) decision matrix: given the
 * selected {@link ManCon} and whether the envelope is a web request, which
 * {@link Network}s may carry it, most preferred first.
 *
 * <p>This is the escalation ladder from the legacy
 * {@code onemfive.routing.CRNetworkManagerService}, made explicit:
 *
 * <ul>
 *   <li><b>LOW / MEDIUM</b> - ordinary overlays are fine; clearnet for web.</li>
 *   <li><b>HIGH</b> (the P2P default) - prefer I2P, then Tor, then non-internet.</li>
 *   <li><b>VERYHIGH</b> - overlays are being attacked; drop Tor, keep I2P, then
 *       non-internet.</li>
 *   <li><b>EXTREME</b> - the internet is blocked for the user; non-internet only.</li>
 *   <li><b>NEO</b> - life-safety; non-internet only, plus multi-copy and long
 *       delays applied elsewhere.</li>
 * </ul>
 */
public final class ManConNetworks {

    private ManConNetworks() {}

    private static final List<Network> NON_INTERNET = Arrays.asList(
            Network.Bluetooth, Network.WiFi, Network.Satellite, Network.FSRadio, Network.LiFi);

    /**
     * Acceptable networks for {@code level}, most preferred first.
     *
     * @param level      the ManCon after {@code ManConStatus.select(...)}
     * @param webRequest true if the envelope carries a URL
     */
    public static List<Network> acceptable(ManCon level, boolean webRequest) {
        List<Network> out = new ArrayList<>();
        switch (level == null ? ManCon.NONE : level) {
            case NEO:
            case EXTREME:
                out.addAll(NON_INTERNET);
                break;
            case VERYHIGH:
                out.add(Network.I2P);
                out.addAll(NON_INTERNET);
                break;
            case HIGH:
                if (webRequest) {
                    out.add(Network.Tor);
                    out.add(Network.I2P);
                } else {
                    out.add(Network.I2P);
                    out.add(Network.Tor);
                }
                out.addAll(NON_INTERNET);
                break;
            case MEDIUM:
                if (webRequest) {
                    out.add(Network.Tor);
                    out.add(Network.I2P);
                    out.add(Network.HTTP);
                } else {
                    out.add(Network.I2P);
                    out.add(Network.Tor);
                    out.add(Network.Bluetooth);
                    out.add(Network.WiFi);
                }
                break;
            case LOW:
            case NONE:
            default:
                if (webRequest) {
                    out.add(Network.HTTP);
                    out.add(Network.Tor);
                    out.add(Network.I2P);
                } else {
                    out.add(Network.I2P);
                    out.add(Network.Tor);
                    out.add(Network.Bluetooth);
                    out.add(Network.WiFi);
                }
                break;
        }
        return out;
    }

    /**
     * The most severe ManCon a set of currently-ready networks can deliver -
     * what {@code ManConStatus.maxAvailable} should be set to.
     *
     * <ul>
     *   <li>any non-internet transport ready &rarr; NEO (nothing depends on the
     *       internet, so every level is reachable);</li>
     *   <li>else I2P ready &rarr; VERYHIGH;</li>
     *   <li>else Tor ready &rarr; HIGH;</li>
     *   <li>else clearnet only &rarr; LOW;</li>
     *   <li>else &rarr; NONE.</li>
     * </ul>
     */
    public static ManCon maxAvailableFor(Iterable<Network> readyNetworks) {
        boolean nonInternet = false, i2p = false, tor = false, clearnet = false;
        for (Network n : readyNetworks) {
            if (Networks.isNonInternet(n)) nonInternet = true;
            else if (n == Network.I2P) i2p = true;
            else if (n == Network.Tor) tor = true;
            else if (Networks.isClearnet(n)) clearnet = true;
        }
        if (nonInternet) return ManCon.NEO;
        if (i2p) return ManCon.VERYHIGH;
        if (tor) return ManCon.HIGH;
        if (clearnet) return ManCon.LOW;
        return ManCon.NONE;
    }
}
