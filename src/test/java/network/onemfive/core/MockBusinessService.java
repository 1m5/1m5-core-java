package network.onemfive.core;

import network.onemfive.core.service.BusinessService;
import ra.common.Envelope;

import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Test business service. The bus instantiates it reflectively, so it exposes what
 * it saw through static collectors.
 */
public class MockBusinessService extends BusinessService {

    public static final ConcurrentLinkedQueue<String> RECEIVED = new ConcurrentLinkedQueue<>();
    public static final ConcurrentLinkedQueue<String> OPERATIONS = new ConcurrentLinkedQueue<>();

    public static void clear() {
        RECEIVED.clear();
        OPERATIONS.clear();
    }

    @Override
    public void handleDocument(Envelope envelope) {
        RECEIVED.add(envelope.getId());
        if (envelope.getRoute() != null) {
            OPERATIONS.add(envelope.getRoute().getOperation());
        }
    }

    @Override
    public void handleHeaders(Envelope envelope) {
        handleDocument(envelope);
    }
}
