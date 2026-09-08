package network.onemfive.core.routing;

/**
 * Backoff schedule for an envelope the router is holding because no path exists
 * yet. Ported from {@code 1m5-android}'s {@code RouterService.RetryStrategy} so
 * the core matches Remnant's current behaviour:
 *
 * <ul>
 *   <li>retry every {@code initialDelayMs} until {@code constantUntilMs} of total
 *       elapsed time;</li>
 *   <li>then multiply the delay by {@code backoffMultiplier} each attempt, capped
 *       at {@code maxDelayMs};</li>
 *   <li>give up (dead-letter) once total elapsed time passes {@code maxElapsedMs}.</li>
 * </ul>
 *
 * Defaults: 20s, constant for 20min, &times;2 backoff, 1h cap, 24h give-up.
 */
public final class RetryStrategy {

    public final long initialDelayMs;
    public final long constantUntilMs;
    public final int backoffMultiplier;
    public final long maxDelayMs;
    public final long maxElapsedMs;

    public RetryStrategy(long initialDelayMs, long constantUntilMs, int backoffMultiplier,
                         long maxDelayMs, long maxElapsedMs) {
        this.initialDelayMs = initialDelayMs;
        this.constantUntilMs = constantUntilMs;
        this.backoffMultiplier = backoffMultiplier;
        this.maxDelayMs = maxDelayMs;
        this.maxElapsedMs = maxElapsedMs;
    }

    /** Remnant's normal-messaging schedule. */
    public static RetryStrategy normalMessaging() {
        return new RetryStrategy(
                20 * 1000L,
                20 * 60 * 1000L,
                2,
                60 * 60 * 1000L,
                24 * 60 * 60 * 1000L);
    }

    /** Fast schedule for tests. */
    public static RetryStrategy of(long initialDelayMs, long constantUntilMs, int backoffMultiplier,
                                   long maxDelayMs, long maxElapsedMs) {
        return new RetryStrategy(initialDelayMs, constantUntilMs, backoffMultiplier, maxDelayMs, maxElapsedMs);
    }

    /** True once the envelope has been held longer than we are willing to wait. */
    public boolean giveUp(long elapsedMs) {
        return elapsedMs >= maxElapsedMs;
    }

    /**
     * The delay before the next attempt.
     *
     * @param elapsedMs      total time the envelope has been held
     * @param currentDelayMs the delay used for the previous attempt (0 for the first)
     */
    public long nextDelayMs(long elapsedMs, long currentDelayMs) {
        if (currentDelayMs <= 0) return initialDelayMs;
        long next = elapsedMs >= constantUntilMs
                ? currentDelayMs * backoffMultiplier
                : initialDelayMs;
        return Math.min(next, maxDelayMs);
    }
}
