package network.onemfive.core.service;

/**
 * Internal business service - domain logic and orchestration. Coordinates other
 * services by building routing slips on the {@link ra.common.Envelope}; holds no
 * durable state of its own (delegate that to a {@link DataService}).
 *
 * The 1M5 intelligent router ({@code network.onemfive.core.routing.RoutingService})
 * is a BusinessService.
 */
public abstract class BusinessService extends CoreService {

    @Override
    public ServiceType getServiceType() {
        return ServiceType.BUSINESS;
    }
}
