package network.onemfive.core.protocol;

import ra.common.Envelope;
import ra.common.network.NetworkService;
import ra.i2p.I2PService;

/**
 * I2P as a 1M5 Core {@link network.onemfive.core.service.ProtocolService}.
 *
 * Wraps {@code i2p-java}'s {@link ra.i2p.I2PService} (an embedded or local I2P
 * router as a {@code NetworkService}). Registered by {@link network.onemfive.core.Daemon}
 * only when {@code 1m5.i2p.enabled=true} - an embedded router reseeds on first
 * start and takes several minutes.
 *
 * Router mode is chosen by {@code ra.i2p.mode} (embedded | local | auto) in the
 * config passed to the bus.
 */
public final class I2PProtocolService extends NetworkServiceProtocol {

    private final I2PService i2p = new I2PService();

    @Override
    protected NetworkService networkService() {
        return i2p;
    }

    @Override
    protected boolean sendOut(Envelope envelope) {
        return Boolean.TRUE.equals(i2p.sendOut(envelope));
    }
}
