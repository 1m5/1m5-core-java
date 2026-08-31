package network.onemfive.core;

import ra.common.Client;
import ra.common.Envelope;
import ra.common.messaging.MessageProducer;

import java.util.ArrayList;
import java.util.List;

/**
 * Captures envelopes a service under test hands to its producer, for unit tests
 * that exercise one service in isolation (no bus).
 */
public class MockProducer implements MessageProducer {

    public final List<Envelope> sent = new ArrayList<>();
    public final List<Envelope> deadLettered = new ArrayList<>();

    @Override
    public boolean send(Envelope envelope) {
        sent.add(envelope);
        return true;
    }

    @Override
    public boolean send(Envelope envelope, Client client) {
        sent.add(envelope);
        return true;
    }

    @Override
    public boolean deadLetter(Envelope envelope) {
        deadLettered.add(envelope);
        return true;
    }
}
