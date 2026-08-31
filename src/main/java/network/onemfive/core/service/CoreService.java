package network.onemfive.core.service;

import ra.common.service.BaseService;

/**
 * Common base for every 1M5 core service. Adds a {@link ServiceType} classification
 * on top of {@link ra.common.service.BaseService} (which already provides the
 * bus wiring: {@code MessageProducer producer}, lifecycle, and the
 * {@code handleDocument/handleEvent/handleCommand/handleHeaders} dispatch).
 *
 * Concrete services extend one of {@link BusinessService}, {@link DataService},
 * or {@link ProtocolService}, never this class directly.
 */
public abstract class CoreService extends BaseService {

    public abstract ServiceType getServiceType();
}
