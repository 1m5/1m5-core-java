package network.onemfive.core;

import network.onemfive.core.identity.IdentityService;
import network.onemfive.core.protocol.I2PProtocolService;
import network.onemfive.core.protocol.TorProtocolService;
import network.onemfive.core.routing.RoutingService;
import ra.common.SystemSettings;

import java.io.File;
import java.io.FileInputStream;
import java.util.Properties;
import java.util.TimeZone;
import java.util.logging.LogManager;
import java.util.logging.Logger;

/**
 * Standalone entry point for the 1M5 core.
 *
 * Extends {@link ra.servicebus.Daemon} - that base owns the {@link ra.servicebus.ServiceBus}
 * lifecycle, the shutdown hook, and the idle loop. This class only supplies the
 * 1M5-specific bits through the hooks: config name, {@code ~/.1m5/core} directories,
 * and which services to register.
 *
 * <p><b>Skeleton scope:</b> registers {@link IdentityService} and
 * {@link RoutingService}. NotificationService, a read-only legacy DIDService, the
 * default I2P/Tor {@code ProtocolService}s, the Bitcoin services, and the localhost
 * Envelope-JSON HTTP API come later (see TODO.md).
 */
public final class Daemon extends ra.servicebus.Daemon {

    private static final Logger LOG = Logger.getLogger(Daemon.class.getName());

    public static void main(String[] args) {
        new Daemon().launch(args);
    }

    @Override
    protected String configName() {
        return "1m5-core.config";
    }

    @Override
    protected void beforeStart(Properties config) {
        Thread.currentThread().setName("1M5-Core");
        applyLogging(config);

        String tz = config.getProperty("1m5.systemTimeZone", "UTC");
        TimeZone.setDefault(TimeZone.getTimeZone(tz));

        String version = config.getProperty("1m5.version", "0.0.0")
                + "." + config.getProperty("1m5.version.build", "0");
        System.setProperty("1m5.version", version);
        LOG.info("1M5 Core version " + version + " (tz " + tz + ")");

        prepareDirectories(config);

        // IdentityService resolves the passphrase from the config first, then the
        // environment; warn only when neither has it.
        String pass = config.getProperty("1m5.pass");
        if (pass == null || pass.isEmpty()) pass = System.getenv("1m5.pass");
        if (pass == null || pass.isEmpty()) {
            LOG.warning("1m5.pass not set (config or env) - the node identity secret will be stored unencrypted.");
        }
    }

    @Override
    protected void onBusStarted(ra.servicebus.ServiceBus bus, Properties config) {
        Core.get().bind(bus, config);

        // Seed the ManCon floor (minRequired) from the user's jurisdiction, if the
        // host supplied one. RSF Press Freedom Index band -> ManCon; see
        // jurisdictions-levels.txt. The user is free to override it afterwards.
        String jurisdiction = config.getProperty("1m5.jurisdiction");
        if (jurisdiction == null || jurisdiction.trim().isEmpty()) jurisdiction = System.getenv("1m5.jurisdiction");
        if (jurisdiction != null && !jurisdiction.trim().isEmpty()) {
            Core.get().manConStatus().applyJurisdiction(jurisdiction);
            LOG.info("ManCon floor from jurisdiction " + jurisdiction.trim().toUpperCase()
                    + " -> " + Core.get().manConStatus().getMinRequired());
        }

        bus.registerAndStartServices(IdentityService.class, RoutingService.class);
        bus.awaitRunning(15_000, IdentityService.class, RoutingService.class);

        if ("true".equalsIgnoreCase(config.getProperty("1m5.i2p.enabled"))) {
            LOG.info("Registering I2P protocol service (1m5.i2p.enabled=true). "
                    + "An embedded I2P router reseeds on first start - this can take several minutes.");
            bus.registerAndStartService(I2PProtocolService.class);
        } else {
            LOG.info("I2P protocol service not registered (set 1m5.i2p.enabled=true to enable).");
        }

        if ("true".equalsIgnoreCase(config.getProperty("1m5.tor.enabled"))) {
            LOG.info("Registering Tor protocol service (1m5.tor.enabled=true). "
                    + "Requires a local Tor daemon (SOCKS 9050 / control 9051).");
            bus.registerAndStartService(TorProtocolService.class);
        } else {
            LOG.info("Tor protocol service not registered (set 1m5.tor.enabled=true to enable).");
        }

        LOG.info("1M5 Core services running.");
    }

    @Override
    protected void onStopping() {
        LOG.info("1M5 Core stopping...");
        Core.reset();
    }

    // -- helpers ---------------------------------------------------

    private void applyLogging(Properties config) {
        String path = config.getProperty("java.util.logging.config.file");
        if (path == null) return;
        File f = new File(path);
        if (!f.exists()) return;
        try (FileInputStream in = new FileInputStream(f)) {
            LogManager.getLogManager().readConfiguration(in);
        } catch (Exception e) {
            LOG.warning("logging config: " + e.getMessage());
        }
    }

    private void prepareDirectories(Properties config) {
        try {
            File base = SystemSettings.getUserAppHomeDir(".1m5", "core", true);
            if (base == null) {
                LOG.severe("could not create ~/.1m5/core");
                return;
            }
            config.put("1m5.dir.base", base.getAbsolutePath());
            for (String name : new String[]{"config", "data", "cache", "pid", "logs", "tmp"}) {
                File d = new File(base, name);
                if (!d.exists() && !d.mkdir()) {
                    LOG.severe("could not create " + d.getAbsolutePath());
                    continue;
                }
                config.put("1m5.dir." + name, d.getAbsolutePath());
            }
            LOG.info("1M5 Core base directory: " + base.getAbsolutePath());
        } catch (Exception e) {
            LOG.severe("directory setup failed: " + e.getMessage());
        }
    }
}
