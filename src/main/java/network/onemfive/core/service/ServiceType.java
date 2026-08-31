package network.onemfive.core.service;

/**
 * Every service registered on the 1M5 core bus is exactly one of:
 *
 * <ul>
 *   <li>{@link #BUSINESS} - internal domain logic / orchestration (messaging,
 *       wallet operations, escrow, price, social, the router itself). Coordinates
 *       other services; owns no durable store.</li>
 *   <li>{@link #DATA} - internal persistence / state (identity vault, peer DB,
 *       contacts, conversation store). Owns storage.</li>
 *   <li>{@link #PROTOCOL} - external transport adapter (I2P, Tor, HTTP, Bluetooth,
 *       ...). Moves bytes between peers and implements {@link Transport}.</li>
 * </ul>
 */
public enum ServiceType {
    BUSINESS,
    DATA,
    PROTOCOL
}
