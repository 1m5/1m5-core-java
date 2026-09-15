package network.onemfive.core.client;

import network.onemfive.core.Core;
import network.onemfive.core.business.BitcoinService;
import network.onemfive.core.identity.IdentityService;
import network.onemfive.core.protocol.HttpProtocolService;
import network.onemfive.core.protocol.I2PProtocolService;
import network.onemfive.core.protocol.TorProtocolService;
import network.onemfive.core.routing.RoutingService;
import network.onemfive.core.service.ProtocolService;
import ra.common.Client;
import ra.common.Envelope;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * {@link CoreClient} over an in-process {@code Core.get()} - the embedding path
 * this module ships for any pure-JVM or Android host (Remnant's {@code :core-host}
 * eventually; {@code 1m5-desktop-java} until it moves to the ADR-0003 RPC API's
 * {@code HttpCoreClient}). See {@code DESIGN.md} §"The embedding contract".
 *
 * <p>Registration mirrors {@link network.onemfive.core.Daemon#onBusStarted}:
 * {@link IdentityService} and {@link RoutingService} always; {@link I2PProtocolService},
 * {@link TorProtocolService}, {@link HttpProtocolService}, {@link BitcoinService} each
 * behind their own {@code 1m5.<name>.enabled} flag. A host that supplies its own
 * transports (Android) simply leaves those flags unset and calls
 * {@link #registerProtocol(ProtocolHandle)} instead.
 */
public final class EmbeddedCoreClient implements CoreClient {

    private volatile Properties config = new Properties();

    @Override
    public boolean start(Map<String, String> configMap) {
        Properties p = new Properties();
        if (configMap != null) p.putAll(configMap);
        this.config = p;

        if (!Core.get().start(p)) return false;

        Core.get().registerServices(IdentityService.class, RoutingService.class);
        if (!Core.get().awaitServices(15_000, IdentityService.class, RoutingService.class)) return false;

        if (flag("1m5.i2p.enabled")) Core.get().registerService(I2PProtocolService.class);
        if (flag("1m5.tor.enabled")) Core.get().registerService(TorProtocolService.class);
        if (flag("1m5.http.enabled")) Core.get().registerService(HttpProtocolService.class);
        if (flag("1m5.bitcoin.enabled")) Core.get().registerService(BitcoinService.class);
        return true;
    }

    @Override
    public void stop() {
        Core.reset();
    }

    @Override
    public boolean send(Msg msg) {
        return Core.get().send(MsgTranslator.toEnvelope(msg));
    }

    @Override
    public boolean send(Msg msg, ReplyHandler callback) {
        Envelope e = MsgTranslator.toEnvelope(msg);
        Client client = callback == null ? null : reply -> callback.onReply(MsgTranslator.toMsg(reply));
        return Core.get().send(e, client);
    }

    @Override
    public CoreInbound registerProtocol(ProtocolHandle handle) {
        if (!Core.get().isRunning()) throw new IllegalStateException("core not started");
        HandleBackedProtocolService svc = HandleBackedProtocolService.registerFor(handle, Core.get().bus(), config);
        if (svc == null) throw new IllegalStateException("could not register protocol handle: " + handle.name());
        return svc.inbound();
    }

    @Override
    public boolean registerChannel(String channel, CoreInbound handler) {
        if (!Core.get().isRunning()) throw new IllegalStateException("core not started");
        return AppChannelService.registerFor(channel, handler, Core.get().bus(), config);
    }

    @Override
    public boolean awaitReady(long timeoutMs, String... channels) {
        if (channels == null || channels.length == 0) return true;
        long deadline = System.currentTimeMillis() + timeoutMs;
        do {
            if (allReady(channels)) return true;
            ra.common.Wait.aMs(50);
        } while (System.currentTimeMillis() < deadline);
        return allReady(channels);
    }

    private boolean allReady(String[] channels) {
        for (String c : channels) {
            boolean found = false;
            for (ProtocolService p : Core.get().readyProtocols()) {
                if (c.equals(p.channelName())) { found = true; break; }
            }
            if (!found) return false;
        }
        return true;
    }

    @Override
    public List<TransportStatus> readyTransports() {
        List<TransportStatus> out = new ArrayList<>();
        for (ProtocolService p : Core.get().protocols()) {
            out.add(new TransportStatus(p.channelName(), p.isReady(), p.getNetworkStatus().name()));
        }
        return out;
    }

    @Override
    public IdentityStatus identityStatus() {
        IdentityService svc = Core.get().bus() == null ? null : Core.get().bus().getService(IdentityService.class);
        if (svc == null) return IdentityStatus.unavailable();
        return new IdentityStatus(svc.getNodePublicKeyHex(), svc.getNodeNpub(), svc.getNodeDid(),
                svc.isEncryptedAtRest(), svc.hasNodeSecret());
    }

    @Override
    public byte[] signAsNode(byte[] canonicalEvent) {
        IdentityService svc = Core.get().bus() == null ? null : Core.get().bus().getService(IdentityService.class);
        if (svc == null) throw new IllegalStateException("IdentityService not registered");
        return svc.signAsNode(canonicalEvent);
    }

    private boolean flag(String key) {
        return "true".equalsIgnoreCase(config.getProperty(key));
    }
}
