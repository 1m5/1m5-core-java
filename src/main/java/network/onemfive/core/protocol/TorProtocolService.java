package network.onemfive.core.protocol;

import ra.common.Envelope;
import ra.common.network.NetworkService;
import ra.tor.TORClientService;

/**
 * Tor as a 1M5 Core {@link network.onemfive.core.service.ProtocolService}.
 *
 * Wraps {@code tor-client-java}'s {@link ra.tor.TORClientService} - a client for a
 * <b>local</b> Tor daemon (SOCKS 9050, control 9051; there is no embedded Tor
 * because the C daemon can't be kept updated in-process). Registered by
 * {@link network.onemfive.core.Daemon} only when {@code 1m5.tor.enabled=true}, and
 * it only starts if a local Tor daemon is reachable (see the tor-client README for
 * the daemon setup).
 */
public final class TorProtocolService extends NetworkServiceProtocol {

    private final TORClientService tor = new TORClientService();

    @Override
    protected NetworkService networkService() {
        return tor;
    }

    @Override
    protected boolean sendOut(Envelope envelope) {
        return Boolean.TRUE.equals(tor.sendOut(envelope));
    }
}
