package network.onemfive.core.business;

import network.onemfive.core.service.BusinessService;
import ra.common.Envelope;

import java.util.Properties;
import java.util.logging.Logger;

/**
 * Bitcoin wallet / escrow operations as a 1M5 Core {@link BusinessService}.
 *
 * <p>Wraps {@code bitcoin-client-java}'s {@link ra.btc.BitcoinService} - a plain
 * {@link ra.common.service.BaseService}, not a {@code NetworkService}, so unlike the protocol
 * adapters in {@code network.onemfive.core.protocol} this composes the client directly rather
 * than through {@code NetworkServiceProtocol}. Registered by {@link network.onemfive.core.Daemon}
 * only when {@code 1m5.bitcoin.enabled=true} (SPV wallet sync via bitcoinj takes time on first
 * start).
 */
public final class BitcoinService extends BusinessService {

    private static final Logger LOG = Logger.getLogger(BitcoinService.class.getName());

    private final ra.btc.BitcoinService client = new ra.btc.BitcoinService();

    @Override
    public boolean start(Properties p) {
        if (!super.start(p)) return false;
        client.setProducer(getProducer());
        client.setObserver(getObserver());
        boolean ok = client.start(config);
        LOG.info("BitcoinService wrapping " + client.getClass().getName() + " started=" + ok);
        return ok;
    }

    @Override
    public boolean shutdown() {
        return client.shutdown() && super.shutdown();
    }

    @Override
    public boolean gracefulShutdown() {
        return client.gracefulShutdown() && super.gracefulShutdown();
    }

    @Override
    public void handleDocument(Envelope envelope) {
        client.handleDocument(envelope);
    }
}
