package network.onemfive.core.business;

import network.onemfive.core.service.BusinessService;
import ra.common.Envelope;
import ra.did.nostr.relay.NostrRelayService;

import java.util.Properties;
import java.util.logging.Logger;

/**
 * Nostr relay connections as a 1M5 Core {@link BusinessService} - the
 * {@code NostrRelayAdapter} named in {@code 1m5-android/DESIGN.md} §"Nostr
 * Integration and Portable Identity", "under routing control, disableable".
 *
 * <p>Wraps {@code did-java}'s {@link ra.did.nostr.relay.NostrRelayService} - a
 * plain {@link ra.common.service.BaseService}, not a {@code NetworkService} -
 * exactly the way {@link BitcoinService} wraps {@code bitcoin-client-java}'s
 * {@code ra.btc.BitcoinService}. Registered by {@link network.onemfive.core.client.EmbeddedCoreClient}
 * only when {@code 1m5.nostr.enabled=true}.
 */
public final class NostrService extends BusinessService {

    private static final Logger LOG = Logger.getLogger(NostrService.class.getName());

    private final NostrRelayService client = new NostrRelayService();

    @Override
    public boolean start(Properties p) {
        if (!super.start(p)) return false;
        client.setProducer(getProducer());
        client.setObserver(getObserver());
        boolean ok = client.start(config);
        LOG.info("NostrService wrapping " + client.getClass().getName() + " started=" + ok);
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
