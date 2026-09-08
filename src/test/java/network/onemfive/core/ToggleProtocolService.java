package network.onemfive.core;

import network.onemfive.core.service.ProtocolService;
import ra.common.Envelope;
import ra.common.network.Network;
import ra.common.network.NetworkStatus;

import java.util.Properties;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Test transport adapter whose {@link Network} and readiness can be flipped at
 * runtime, so router hold / retry / relay paths can be exercised.
 */
public class ToggleProtocolService extends ProtocolService {

    public static volatile Network network = Network.I2P;
    public static volatile boolean ready = false;
    public static final ConcurrentLinkedQueue<String> SENT = new ConcurrentLinkedQueue<>();

    public static void reset(Network net, boolean isReady) {
        network = net;
        ready = isReady;
        SENT.clear();
    }

    public static void setReady(boolean isReady) {
        ready = isReady;
    }

    @Override
    public boolean start(Properties p) {
        return super.start(p);
    }

    @Override
    public Network getNetwork() {
        return network;
    }

    @Override
    public NetworkStatus getNetworkStatus() {
        return ready ? NetworkStatus.CONNECTED : NetworkStatus.DISCONNECTED;
    }

    @Override
    public boolean send(Envelope envelope) {
        SENT.add(envelope.getId());
        return true;
    }
}
