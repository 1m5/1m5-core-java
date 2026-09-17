package network.onemfive.core.protocol;

import ra.common.Envelope;
import ra.common.network.NetworkService;
import ra.http.HTTPService;

/**
 * Plain HTTP as a 1M5 Core {@link network.onemfive.core.service.ProtocolService}.
 *
 * Wraps {@code http-client-java}'s {@link ra.http.HTTPService} - a direct (non-anonymized)
 * HTTP/HTTPS client {@code NetworkService}. Registered by {@link network.onemfive.core.Daemon}
 * only when {@code 1m5.http.enabled=true}. Unlike I2P/Tor this network offers no anonymity;
 * it exists so the router has a clearnet fallback/peer-discovery path.
 */
public final class HttpProtocolService extends NetworkServiceProtocol {

    private final HTTPService http = new HTTPService();

    @Override
    protected NetworkService networkService() {
        return http;
    }

    @Override
    protected boolean sendOut(Envelope envelope) {
        return Boolean.TRUE.equals(http.sendOut(envelope));
    }
}
