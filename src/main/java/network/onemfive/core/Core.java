package network.onemfive.core;

import network.onemfive.core.service.CoreService;
import network.onemfive.core.service.ProtocolService;
import ra.common.Client;
import ra.common.Envelope;
import ra.servicebus.ServiceBus;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Logger;

/**
 * Process-wide handle to the running 1M5 core: the active {@link ServiceBus} plus
 * the 1M5-specific runtime state ({@link ManConStatus}).
 *
 * <p>Two ways in:
 * <ul>
 *   <li>embedded / tests: {@link #start(Properties)} builds and owns a
 *       {@link ServiceBus};</li>
 *   <li>daemon: {@link Daemon} owns the bus and calls {@link #bind(ServiceBus)}.</li>
 * </ul>
 *
 * <p>Service management (register, start, discover, await) is delegated straight to
 * {@link ServiceBus} - 1M5 Core adds only ManCon and the protocol-by-readiness view
 * the router needs. Call {@link #reset()} between tests.
 */
public final class Core {

    private static final Logger LOG = Logger.getLogger(Core.class.getName());

    private static volatile Core instance;

    private final ManConStatus manConStatus = new ManConStatus();
    private final List<ManConStatusListener> manConListeners = new CopyOnWriteArrayList<>();

    private ServiceBus bus;
    private Properties config;
    private boolean ownsBus;
    private volatile boolean running;

    private Core() {}

    public static Core get() {
        Core c = instance;
        if (c == null) {
            synchronized (Core.class) {
                c = instance;
                if (c == null) instance = c = new Core();
            }
        }
        return c;
    }

    /** Tear down (only stopping the bus if this Core created it) and drop the singleton. */
    public static synchronized void reset() {
        if (instance != null) {
            instance.stop();
            instance = null;
        }
    }

    // -- lifecycle ---------------------------------------------------

    /** Build and own a {@link ServiceBus}. No-op if a bus is already bound. */
    public synchronized boolean start(Properties properties) {
        if (running) return true;
        this.config = properties != null ? properties : new Properties();
        this.bus = new ServiceBus(config);
        this.ownsBus = true;
        this.running = bus.start(config);
        if (running) LOG.info("1M5 Core started (owns bus)");
        return running;
    }

    /** Use a bus whose lifecycle is managed elsewhere (the daemon). */
    public synchronized void bind(ServiceBus bus, Properties config) {
        this.bus = bus;
        this.config = config;
        this.ownsBus = false;
        this.running = true;
        LOG.info("1M5 Core bound to externally-managed bus");
    }

    public synchronized void stop() {
        if (!running) return;
        running = false;
        if (ownsBus && bus != null) {
            try { bus.gracefulShutdown(); }
            catch (RuntimeException e) { LOG.warning("bus shutdown: " + e.getMessage()); }
        }
        LOG.info("1M5 Core stopped");
    }

    public boolean isRunning() {
        return running;
    }

    public ServiceBus bus() {
        return bus;
    }

    // -- services (delegated) --------------------------------------

    public boolean registerService(Class<? extends CoreService> serviceClass) {
        if (!running) throw new IllegalStateException("Core not started");
        return bus.registerAndStartService(serviceClass);
    }

    @SafeVarargs
    public final void registerServices(Class<? extends CoreService>... serviceClasses) {
        for (Class<? extends CoreService> c : serviceClasses) registerService(c);
    }

    public boolean awaitServices(long timeoutMs, Class<?>... serviceClasses) {
        return bus.awaitRunning(timeoutMs, serviceClasses);
    }

    public boolean send(Envelope e) {
        return bus.send(e);
    }

    public boolean send(Envelope e, Client callback) {
        return bus.send(e, callback);
    }

    // -- protocol view for the router -----------------------------

    /** All running protocol adapters. */
    public Collection<ProtocolService> protocols() {
        return bus == null ? new ArrayList<>() : bus.findRunningServices(ProtocolService.class);
    }

    /** Protocol adapters currently connected and able to carry an envelope. */
    public List<ProtocolService> readyProtocols() {
        List<ProtocolService> ready = new ArrayList<>();
        for (ProtocolService p : protocols()) {
            if (p.isReady()) ready.add(p);
        }
        return ready;
    }

    // -- ManCon --------------------------------------------------

    public ManConStatus manConStatus() {
        return manConStatus;
    }

    public void addManConStatusListener(ManConStatusListener l) {
        manConListeners.add(l);
    }

    public void manConStatusChanged() {
        for (ManConStatusListener l : manConListeners) {
            try { l.run(); }
            catch (RuntimeException e) { LOG.warning("ManCon listener: " + e.getMessage()); }
        }
    }
}
