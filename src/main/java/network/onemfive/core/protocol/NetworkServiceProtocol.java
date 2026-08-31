package network.onemfive.core.protocol;

import network.onemfive.core.service.ProtocolService;
import ra.common.Envelope;
import ra.common.network.Network;
import ra.common.network.NetworkService;
import ra.common.network.NetworkStatus;

import java.util.Properties;
import java.util.logging.Logger;

/**
 * Adapts a {@link ra.common.network.NetworkService} (the RA base that {@code i2p-java},
 * {@code tor-client}, {@code bluetooth-client} all extend) to 1M5 Core's
 * {@link ProtocolService} / {@code Transport} contract, so the router can discover
 * it via {@code serviceBus.findRunningServices(ProtocolService.class)}.
 *
 * <p>The wrapper is the bus-registered service; the wrapped {@code NetworkService}
 * is driven by this class (given the same {@code MessageProducer}, so its inbound
 * envelopes flow back onto the bus). A subclass supplies the concrete
 * {@code NetworkService} and how to send over it.
 */
public abstract class NetworkServiceProtocol extends ProtocolService {

    private static final Logger LOG = Logger.getLogger(NetworkServiceProtocol.class.getName());

    /** The wrapped RA network service (I2P, Tor, Bluetooth, ...). Same instance every call. */
    protected abstract NetworkService networkService();

    /** Carry an envelope out over the wrapped service (call its public {@code sendOut}). */
    protected abstract boolean sendOut(Envelope envelope);

    @Override
    public Network getNetwork() {
        Network n = networkService().getNetworkState().network;
        return n != null ? n : Network.HTTP;
    }

    @Override
    public NetworkStatus getNetworkStatus() {
        return networkService().getNetworkState().networkStatus;
    }

    @Override
    public boolean start(Properties p) {
        if (!super.start(p)) return false;
        NetworkService delegate = networkService();
        delegate.setProducer(getProducer());
        delegate.setObserver(getObserver());
        boolean ok = delegate.start(config);
        setNetworkStatus(getNetworkStatus());
        LOG.info(getClass().getSimpleName() + " wrapping " + delegate.getClass().getName()
                + " started=" + ok + " status=" + getNetworkStatus());
        return ok;
    }

    @Override
    public boolean shutdown() {
        boolean d = networkService().shutdown();
        return super.shutdown() && d;
    }

    @Override
    public boolean gracefulShutdown() {
        boolean d = networkService().gracefulShutdown();
        return super.gracefulShutdown() && d;
    }

    @Override
    public final boolean send(Envelope envelope) {
        return sendOut(envelope);
    }
}
