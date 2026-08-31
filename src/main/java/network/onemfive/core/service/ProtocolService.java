package network.onemfive.core.service;

import ra.common.Envelope;
import ra.common.network.NetworkStatus;

import java.util.logging.Logger;

/**
 * External protocol service - a transport adapter (I2P, Tor, HTTP, Bluetooth, ...).
 *
 * <p>The router discovers protocol adapters through the bus
 * ({@code serviceBus.findRunningServices(ProtocolService.class)}) and reads
 * {@link #getNetworkStatus()} to decide routing. Concrete adapters live per
 * platform: the default JVM daemon wraps the {@code resolvingarchitecture} network
 * clients (or {@code i2p-java} / {@code tor-java}); an Android host supplies adapters
 * over the embedded I2P router and {@code tor-android}, implementing this same class.
 *
 * <p>The standard inbound operation is {@link #OPERATION_SEND}.
 */
public abstract class ProtocolService extends CoreService implements Transport {

    private static final Logger LOG = Logger.getLogger(ProtocolService.class.getName());

    public static final String OPERATION_SEND = "SEND";

    protected volatile NetworkStatus networkStatus = NetworkStatus.CLOSED;

    @Override
    public ServiceType getServiceType() {
        return ServiceType.PROTOCOL;
    }

    @Override
    public NetworkStatus getNetworkStatus() {
        return networkStatus;
    }

    protected void setNetworkStatus(NetworkStatus status) {
        this.networkStatus = status;
    }

    @Override
    public void handleDocument(Envelope envelope) {
        dispatch(envelope);
    }

    @Override
    public void handleHeaders(Envelope envelope) {
        dispatch(envelope);
    }

    private void dispatch(Envelope envelope) {
        String op = envelope.getRoute() != null ? envelope.getRoute().getOperation() : null;
        if (OPERATION_SEND.equals(op)) {
            if (!send(envelope)) {
                LOG.warning(getNetwork() + " could not send envelope " + envelope.getId());
                envelope.addErrorMessage(getNetwork() + " send failed");
            }
        } else {
            LOG.warning(getClass().getName() + " received unsupported operation: " + op);
        }
    }
}
