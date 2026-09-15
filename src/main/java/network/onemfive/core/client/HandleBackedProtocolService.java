package network.onemfive.core.client;

import network.onemfive.core.service.ProtocolService;
import ra.common.Envelope;
import ra.common.Wait;
import ra.common.network.Network;
import ra.common.network.NetworkStatus;
import ra.common.service.ServiceNotAccessibleException;
import ra.common.service.ServiceNotSupportedException;
import ra.servicebus.ServiceBus;

import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Logger;

/**
 * The synthetic {@code ProtocolService} a host-supplied {@link ProtocolHandle}
 * (Android's I2P/Tor adapters, ...) is wrapped in to sit on the bus. Registered
 * directly under the handle's own {@link ProtocolHandle#name()} (not this class's
 * name) via {@link #registerFor}, so the router's routes and every
 * {@code channelName()} lookup work unmodified for a host-supplied transport, the
 * same as for a standard adapter like {@code I2PProtocolService}.
 *
 * <p>Needs a public no-arg constructor for {@code ServiceBus}'s reflective
 * registration, so {@link #registerFor} hands it the specific handle to bind to
 * through a short-lived, lock-guarded static slot rather than a constructor
 * argument - registration constructs the instance synchronously, inside the
 * caller's own thread ({@code ra.servicebus.ServiceBus#registerService} does not
 * hand off to another thread for construction, only {@code startService} does), so
 * the handoff is race-free. This keeps {@code service-bus-java} unchanged, per
 * {@code TODO.md} §"Embedding contract".
 */
public final class HandleBackedProtocolService extends ProtocolService {

    private static final Logger LOG = Logger.getLogger(HandleBackedProtocolService.class.getName());

    private static final Object BIND_LOCK = new Object();
    private static ProtocolHandle pendingHandle;
    private static final ConcurrentMap<String, HandleBackedProtocolService> INSTANCES = new ConcurrentHashMap<>();

    private final ProtocolHandle handle;

    /** For {@code ServiceBus}'s reflective construction only - see {@link #registerFor}. */
    public HandleBackedProtocolService() {
        this.handle = pendingHandle;
        if (handle != null) INSTANCES.put(handle.name(), this);
    }

    /**
     * Register {@code handle} on the bus under its own {@link ProtocolHandle#name()}
     * and start it. Returns the running instance, or {@code null} if registration
     * or start did not complete within a few seconds.
     */
    static HandleBackedProtocolService registerFor(ProtocolHandle handle, ServiceBus bus, Properties config) {
        boolean registered;
        synchronized (BIND_LOCK) {
            pendingHandle = handle;
            try {
                registered = bus.registerService(handle.name(), HandleBackedProtocolService.class.getName(), config);
            } catch (ServiceNotAccessibleException | ServiceNotSupportedException e) {
                LOG.warning("could not register protocol handle " + handle.name() + ": " + e.getMessage());
                registered = false;
            } finally {
                pendingHandle = null;
            }
        }
        if (!registered) return null;
        bus.startService(handle.name());
        long deadline = System.currentTimeMillis() + 5_000L;
        while (!bus.getRunningServiceNames().contains(handle.name()) && System.currentTimeMillis() < deadline) {
            Wait.aMs(20);
        }
        return INSTANCES.get(handle.name());
    }

    @Override
    public boolean start(Properties p) {
        if (!super.start(p)) return false;
        return handle != null;
    }

    @Override
    public boolean shutdown() {
        unbind();
        return super.shutdown();
    }

    @Override
    public boolean gracefulShutdown() {
        unbind();
        return super.gracefulShutdown();
    }

    private void unbind() {
        if (handle != null) INSTANCES.remove(handle.name(), this);
    }

    @Override
    public String channelName() {
        return handle.name();
    }

    @Override
    public Network getNetwork() {
        return handle.network();
    }

    @Override
    public NetworkStatus getNetworkStatus() {
        TransportStatus s = handle.status();
        return s != null && s.isReady() ? NetworkStatus.CONNECTED : NetworkStatus.CLOSED;
    }

    @Override
    public String getLocalAddress() {
        return handle.localAddress();
    }

    @Override
    public boolean send(Envelope envelope) {
        return handle.send(MsgTranslator.toMsg(envelope));
    }

    /** The sink {@code EmbeddedCoreClient.registerProtocol} returns to the host. */
    CoreInbound inbound() {
        return msg -> getProducer().send(MsgTranslator.toEnvelope(msg));
    }
}
