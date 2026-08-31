package network.onemfive.core;

import network.onemfive.core.service.ProtocolService;
import ra.common.Envelope;
import ra.common.network.Network;
import ra.common.network.NetworkStatus;

import java.util.Properties;
import java.util.concurrent.ConcurrentLinkedQueue;

/** Test transport adapter: comes up CONNECTED and records what it is asked to send. */
public class MockProtocolService extends ProtocolService {

    public static final ConcurrentLinkedQueue<String> SENT = new ConcurrentLinkedQueue<>();

    public static void clear() {
        SENT.clear();
    }

    @Override
    public boolean start(Properties p) {
        boolean ok = super.start(p);
        setNetworkStatus(NetworkStatus.CONNECTED);
        return ok;
    }

    @Override
    public Network getNetwork() {
        return Network.HTTP;
    }

    @Override
    public boolean send(Envelope envelope) {
        SENT.add(envelope.getId());
        return true;
    }
}
