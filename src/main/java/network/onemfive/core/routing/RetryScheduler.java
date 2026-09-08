package network.onemfive.core.routing;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Schedules a held envelope's next routing attempt. Abstracted so tests can drive
 * retries synchronously instead of waiting real seconds.
 */
public interface RetryScheduler {

    /** Run {@code task} after {@code delayMs}, replacing any pending task for {@code key}. */
    void schedule(String key, long delayMs, Runnable task);

    /** Cancel a pending task (the envelope routed, or was dead-lettered). */
    void cancel(String key);

    /** Cancel everything and release resources. */
    void shutdown();

    /** Default: a single-threaded {@link ScheduledThreadPoolExecutor}. */
    final class Default implements RetryScheduler {

        private final ScheduledThreadPoolExecutor exec;
        private final Map<String, ScheduledFuture<?>> pending = new ConcurrentHashMap<>();

        public Default() {
            this.exec = new ScheduledThreadPoolExecutor(1, r -> {
                Thread t = new Thread(r, "1m5-router-retry");
                t.setDaemon(true);
                return t;
            });
            this.exec.setRemoveOnCancelPolicy(true);
        }

        @Override
        public void schedule(String key, long delayMs, Runnable task) {
            cancel(key);
            pending.put(key, exec.schedule(() -> {
                pending.remove(key);
                task.run();
            }, Math.max(0L, delayMs), TimeUnit.MILLISECONDS));
        }

        @Override
        public void cancel(String key) {
            ScheduledFuture<?> f = pending.remove(key);
            if (f != null) f.cancel(false);
        }

        @Override
        public void shutdown() {
            pending.clear();
            exec.shutdownNow();
        }
    }

    /** Test double: retries are only run when {@link Manual#runDue()} is called. */
    final class Manual implements RetryScheduler {

        public static final class Entry {
            public final long delayMs;
            public final Runnable task;
            Entry(long delayMs, Runnable task) { this.delayMs = delayMs; this.task = task; }
        }

        private final Map<String, Entry> pending = new ConcurrentHashMap<>();

        @Override
        public void schedule(String key, long delayMs, Runnable task) {
            pending.put(key, new Entry(delayMs, task));
        }

        @Override
        public void cancel(String key) {
            pending.remove(key);
        }

        @Override
        public void shutdown() {
            pending.clear();
        }

        public int pendingCount() { return pending.size(); }

        public boolean isPending(String key) { return pending.containsKey(key); }

        public Long delayFor(String key) {
            Entry e = pending.get(key);
            return e == null ? null : e.delayMs;
        }

        /** Fire every currently-pending task (each removes itself first, like Default). */
        public void runDue() {
            for (Map.Entry<String, Entry> e : new java.util.ArrayList<>(pending.entrySet())) {
                if (pending.remove(e.getKey()) != null) {
                    e.getValue().task.run();
                }
            }
        }
    }
}
