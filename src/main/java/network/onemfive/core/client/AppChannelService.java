package network.onemfive.core.client;

import network.onemfive.core.service.BusinessService;
import ra.common.Envelope;
import ra.common.Wait;
import ra.common.service.ServiceNotAccessibleException;
import ra.common.service.ServiceNotSupportedException;
import ra.servicebus.ServiceBus;

import java.util.Properties;
import java.util.logging.Logger;

/**
 * The synthetic {@link BusinessService} a host-registered app inbox handler
 * sits behind on the bus - the counterpart to {@link HandleBackedProtocolService}
 * for the app's own business channel rather than a network transport. Closes
 * the gap {@link CoreInbound}'s javadoc and {@code TODO.md} §"Embedding
 * contract" used to describe as open: before this, a {@link Msg}'s slip could
 * terminate at a business channel the app itself owns (e.g. {@code "messaging"})
 * but nothing was listening there - {@link HandleBackedProtocolService#inbound()}
 * re-injected the envelope onto the bus with no destination for it to reach.
 *
 * <p>Registered under the caller's own channel name via {@link #registerFor},
 * the same lock-guarded static handoff {@link HandleBackedProtocolService} uses
 * and for the same reason: {@code ServiceBus.registerService} needs a public
 * no-arg constructor but constructs synchronously, so the handoff is race-free.
 * No {@code INSTANCES} map is needed here (unlike that class): the host already
 * holds its own handler, so nothing is ever looked up after registration.
 */
public final class AppChannelService extends BusinessService {

    private static final Logger LOG = Logger.getLogger(AppChannelService.class.getName());

    private static final Object BIND_LOCK = new Object();
    private static CoreInbound pendingHandler;

    private final CoreInbound handler;

    /** For {@code ServiceBus}'s reflective construction only - see {@link #registerFor}. */
    public AppChannelService() {
        this.handler = pendingHandler;
    }

    /**
     * Register {@code handler} on the bus under {@code channel} and start it.
     * Returns whether registration and start both completed.
     */
    static boolean registerFor(String channel, CoreInbound handler, ServiceBus bus, Properties config) {
        boolean registered;
        synchronized (BIND_LOCK) {
            pendingHandler = handler;
            try {
                registered = bus.registerService(channel, AppChannelService.class.getName(), config);
            } catch (ServiceNotAccessibleException | ServiceNotSupportedException e) {
                LOG.warning("could not register app channel " + channel + ": " + e.getMessage());
                registered = false;
            } finally {
                pendingHandler = null;
            }
        }
        if (!registered) return false;
        bus.startService(channel);
        long deadline = System.currentTimeMillis() + 5_000L;
        while (!bus.getRunningServiceNames().contains(channel) && System.currentTimeMillis() < deadline) {
            Wait.aMs(20);
        }
        return bus.getRunningServiceNames().contains(channel);
    }

    @Override
    public boolean start(Properties p) {
        if (!super.start(p)) return false;
        return handler != null;
    }

    @Override
    public void handleDocument(Envelope envelope) {
        if (handler != null) handler.accept(MsgTranslator.toMsg(envelope));
    }
}
