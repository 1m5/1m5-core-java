package network.onemfive.core.service;

/**
 * Internal data service - owns durable local state (identity vault, peer DB,
 * contacts, conversation store, InfoVault). Should register its bus channel with
 * {@link ra.common.service.ServiceLevel#AtLeastOnce} or
 * {@link ra.common.service.ServiceLevel#ExactlyOnce} so writes survive a crash.
 */
public abstract class DataService extends CoreService {

    @Override
    public ServiceType getServiceType() {
        return ServiceType.DATA;
    }
}
