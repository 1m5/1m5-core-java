package network.onemfive.core.client;

/**
 * Completion callback for {@link CoreClient#send(Msg, ReplyHandler)}. Fires once
 * when the underlying routing slip completes (success or final failure - inspect
 * the reply {@link Msg}'s {@code x.error.*} headers to tell them apart).
 */
@FunctionalInterface
public interface ReplyHandler {
    void onReply(Msg reply);
}
