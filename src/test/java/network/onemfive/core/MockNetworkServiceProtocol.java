package network.onemfive.core;

import network.onemfive.core.protocol.NetworkServiceProtocol;
import ra.common.Envelope;
import ra.common.network.NetworkService;

/**
 * Test adapter: wraps a {@link MockNetworkService} the same way
 * {@code I2PProtocolService} wraps {@code ra.i2p.I2PService}.
 */
public class MockNetworkServiceProtocol extends NetworkServiceProtocol {

    private final MockNetworkService delegate = new MockNetworkService();

    @Override
    protected NetworkService networkService() {
        return delegate;
    }

    @Override
    protected boolean sendOut(Envelope envelope) {
        return Boolean.TRUE.equals(delegate.sendOut(envelope));
    }
}
