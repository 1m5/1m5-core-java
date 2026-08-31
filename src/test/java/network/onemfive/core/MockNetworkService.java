package network.onemfive.core;

import ra.common.Envelope;
import ra.common.network.Network;
import ra.common.network.NetworkService;
import ra.common.network.NetworkStatus;

import java.util.Properties;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * A trivial {@link ra.common.network.NetworkService} - the RA base that
 * {@code i2p-java} / {@code tor-client} / {@code bluetooth-client} extend - so the
 * {@code NetworkServiceProtocol} adapter can be exercised without a real router.
 */
public class MockNetworkService extends NetworkService {

    public static final ConcurrentLinkedQueue<String> SENT_OUT = new ConcurrentLinkedQueue<>();

    public static void clear() {
        SENT_OUT.clear();
    }

    public MockNetworkService() {
        super(Network.I2P);
    }

    @Override
    public boolean start(Properties p) {
        boolean ok = super.start(p);
        updateNetworkStatus(NetworkStatus.CONNECTED);
        return ok;
    }

    @Override
    public boolean shutdown() {
        updateNetworkStatus(NetworkStatus.DISCONNECTED);
        return true;
    }

    @Override
    public boolean gracefulShutdown() {
        return shutdown();
    }

    /** Widened to public so the adapter (different package) can call it. */
    @Override
    public Boolean sendOut(Envelope envelope) {
        SENT_OUT.add(envelope.getId());
        return true;
    }
}
