package network.onemfive.core;

import network.onemfive.core.service.BusinessService;
import ra.common.Envelope;

import java.util.concurrent.ConcurrentLinkedQueue;

/** A second business service, to prove the router slip walks multiple hops in order. */
public class MockRelayService extends BusinessService {

    public static final ConcurrentLinkedQueue<String> RECEIVED = new ConcurrentLinkedQueue<>();

    public static void clear() {
        RECEIVED.clear();
    }

    @Override
    public void handleDocument(Envelope envelope) {
        RECEIVED.add(envelope.getId());
    }

    @Override
    public void handleHeaders(Envelope envelope) {
        RECEIVED.add(envelope.getId());
    }
}
