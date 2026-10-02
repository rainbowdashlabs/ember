/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.storage.backend.tree;

import dev.chojo.ember.feature.storage.backend.StorageUnavailableException;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * A bounded pool of protocol sessions for one backend.
 *
 * <p>At most {@link #SIZE} trees are open at once; a caller beyond that waits at most
 * {@link #ACQUIRE_TIMEOUT} and is then refused with {@link StorageUnavailableException}. A discarded or
 * broken tree is closed on return, and idle trees beyond the first are closed after {@link #IDLE_CLOSE}.
 * When a server refuses another tree while others it opened still work, the pool stays below that limit.
 *
 * <p>After {@link #FAILURES_BEFORE_REFUSING} failed connects in a row the pool refuses at once for
 * {@link #REFUSAL_WINDOW} instead of every caller waiting out a connect; the health check still tries,
 * and the first connect that succeeds ends the window. A lease held longer than {@link #LEAK_AFTER} is
 * logged once with the stack of whoever took it, since a caller that never closes what it read would
 * otherwise drain the pool without a trace.
 *
 * @param <T> the kind of tree
 */
public final class LeasePool<T extends FileTree> implements TreeSource<T> {
    /** How many trees may be open at once. */
    public static final int SIZE = 4;

    /** How long a caller waits for a tree. */
    public static final Duration ACQUIRE_TIMEOUT = Duration.ofSeconds(10);

    /** How long an idle tree beyond the first is kept. */
    public static final Duration IDLE_CLOSE = Duration.ofMinutes(5);

    /** How long a lease may be held before it is reported. */
    public static final Duration LEAK_AFTER = Duration.ofMinutes(10);

    /** How long closing the pool waits for lent trees to come back. */
    public static final Duration DRAIN_LIMIT = Duration.ofSeconds(30);

    /** How many failed connects in a row make the pool refuse at once. */
    public static final int FAILURES_BEFORE_REFUSING = 3;

    /** How long the pool then refuses at once. */
    public static final Duration REFUSAL_WINDOW = Duration.ofSeconds(30);

    private static final Logger log = LoggerFactory.getLogger(LeasePool.class);

    private final String name;
    private final Opener<T> opener;
    private final Duration acquireTimeout;
    private final Executor drainer;
    private final Clock clock;
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition changed = lock.newCondition();
    private final Deque<Idle<T>> idle = new ArrayDeque<>();
    private final Set<PooledLease> lent = new HashSet<>();
    private int open;
    private int limit;
    private int failedOpens;
    private Instant refusedUntil;
    private boolean closed;

    /**
     * @param name    what the pool is for, in the messages it refuses with
     * @param opener  opens one tree
     * @param drainer where a closed pool waits for the trees still lent, the task scheduler in the application
     */
    public LeasePool(String name, Opener<T> opener, Executor drainer) {
        this(name, opener, SIZE, ACQUIRE_TIMEOUT, drainer, Clock.systemUTC());
    }

    LeasePool(String name, Opener<T> opener, int maxSize, Duration acquireTimeout, Executor drainer, Clock clock) {
        this.name = name;
        this.opener = opener;
        this.acquireTimeout = acquireTimeout;
        this.drainer = drainer;
        this.clock = clock;
        this.limit = maxSize;
    }

    @Override
    public Lease<T> acquire() {
        return acquire(false);
    }

    /** Lends a tree even while the pool refuses a server that failed repeatedly. */
    @Override
    public Lease<T> acquireForProbe() {
        return acquire(true);
    }

    private Lease<T> acquire(boolean probe) {
        long deadline = System.nanoTime() + acquireTimeout.toNanos();
        while (true) {
            var ready = idleOrSlot(deadline, probe);
            if (ready != null) return lend(ready);
            try {
                T tree = opener.open();
                openSucceeded();
                return lend(tree);
            } catch (IOException | RuntimeException e) {
                if (!openFailedBelowTheServersLimit()) {
                    throw new StorageUnavailableException(name + " cannot be reached: " + e.getMessage(), e);
                }
                log.warn("{} refused another connection, staying at {} from now on", name, limit, e);
            }
        }
    }

    /** How many trees are open, lent or idle. */
    int openTrees() {
        lock.lock();
        try {
            return open;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Closes the idle trees at once and every lent one as it comes back, then runs {@code afterwards}.
     * While trees are lent the wait runs on the drainer, or on the caller when the drainer refuses, and
     * a tree still out after {@link #DRAIN_LIMIT} is closed underneath its caller.
     */
    @Override
    public void close(Runnable afterwards) {
        List<T> toClose = new ArrayList<>();
        boolean drain;
        lock.lock();
        try {
            if (closed) return;
            closed = true;
            idle.forEach(entry -> toClose.add(entry.tree()));
            open -= idle.size();
            idle.clear();
            drain = !lent.isEmpty();
            changed.signalAll();
        } finally {
            lock.unlock();
        }
        toClose.forEach(FileTree::close);
        if (!drain) {
            afterwards.run();
            return;
        }
        Runnable finish = () -> {
            closeStragglers();
            afterwards.run();
        };
        try {
            drainer.execute(finish);
        } catch (RejectedExecutionException stopping) {
            finish.run();
        }
    }

    /** An idle tree, or null when the caller may open one; waits while neither is to be had. */
    private @Nullable T idleOrSlot(long deadline, boolean probe) {
        lock.lock();
        try {
            while (true) {
                if (closed) throw new StorageUnavailableException(name + " has been closed");
                reportLongLeases();
                closeLongIdle();
                while (!idle.isEmpty()) {
                    T tree = idle.pop().tree();
                    if (tree.isUsable()) return tree;
                    open--;
                    tree.close();
                }
                if (!probe && refusedUntil != null && clock.instant().isBefore(refusedUntil)) {
                    throw new StorageUnavailableException(name + " failed to connect " + FAILURES_BEFORE_REFUSING
                            + " times in a row and is not tried again before " + refusedUntil);
                }
                if (open < limit) {
                    open++;
                    return null;
                }
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0) {
                    throw new StorageUnavailableException(name + " is busy, no connection came free in time");
                }
                changed.awaitNanos(remaining);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new StorageUnavailableException(name + " was interrupted while waiting for a connection", e);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Gives the slot of a failed open back and says whether it hit the server's limit rather than an
     * outage, which is the case when another tree it opened still works.
     */
    private boolean openFailedBelowTheServersLimit() {
        lock.lock();
        try {
            open--;
            changed.signalAll();
            boolean othersWork = idle.stream().anyMatch(entry -> entry.tree().isUsable())
                    || lent.stream().anyMatch(lease -> lease.tree.isUsable());
            if (othersWork && open > 0) {
                limit = Math.max(1, open);
                return true;
            }
            failedOpens++;
            if (failedOpens >= FAILURES_BEFORE_REFUSING)
                refusedUntil = clock.instant().plus(REFUSAL_WINDOW);
            return false;
        } finally {
            lock.unlock();
        }
    }

    private void openSucceeded() {
        lock.lock();
        try {
            failedOpens = 0;
            refusedUntil = null;
        } finally {
            lock.unlock();
        }
    }

    private Lease<T> lend(T tree) {
        var lease = new PooledLease(tree, clock.instant());
        lock.lock();
        try {
            lent.add(lease);
        } finally {
            lock.unlock();
        }
        return lease;
    }

    private void giveBack(PooledLease lease, boolean discarded) {
        boolean keep;
        lock.lock();
        try {
            lent.remove(lease);
            keep = !discarded && !closed && lease.tree.isUsable();
            if (keep) {
                idle.push(new Idle<>(lease.tree, clock.instant()));
            } else {
                open--;
            }
            changed.signalAll();
        } finally {
            lock.unlock();
        }
        if (!keep) lease.tree.close();
    }

    private void closeLongIdle() {
        Instant cutoff = clock.instant().minus(IDLE_CLOSE);
        while (idle.size() > 1 && idle.peekLast().since().isBefore(cutoff)) {
            open--;
            idle.pollLast().tree().close();
        }
    }

    private void reportLongLeases() {
        Instant cutoff = clock.instant().minus(LEAK_AFTER);
        for (PooledLease held : lent) {
            if (!held.reported && held.since.isBefore(cutoff)) {
                held.reported = true;
                log.warn(
                        "A connection of {} has been held since {} and was never given back",
                        name,
                        held.since,
                        held.taker);
            }
        }
    }

    private void closeStragglers() {
        List<T> stragglers = new ArrayList<>();
        lock.lock();
        try {
            long remaining = DRAIN_LIMIT.toNanos();
            while (!lent.isEmpty() && remaining > 0) {
                remaining = changed.awaitNanos(remaining);
            }
            lent.forEach(lease -> stragglers.add(lease.tree));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            lock.unlock();
        }
        if (!stragglers.isEmpty()) log.warn("{} closed {} connection(s) still in use", name, stragglers.size());
        stragglers.forEach(FileTree::close);
    }

    /**
     * Opens one tree, connecting and signing in as far as needed.
     *
     * @param <T> the kind of tree
     */
    @FunctionalInterface
    public interface Opener<T extends FileTree> {
        /** @throws IOException when the server cannot be reached or refuses the sign-in */
        T open() throws IOException;
    }

    private record Idle<T>(T tree, Instant since) {}

    private final class PooledLease implements Lease<T> {
        private final T tree;
        private final Instant since;
        private final Throwable taker = new Throwable("Lease of " + name + " taken here");
        private final AtomicBoolean returned = new AtomicBoolean();
        private volatile boolean discarded;
        private boolean reported;

        private PooledLease(T tree, Instant since) {
            this.tree = tree;
            this.since = since;
        }

        @Override
        public T tree() {
            return tree;
        }

        @Override
        public void discard() {
            discarded = true;
        }

        @Override
        public void close() {
            if (returned.compareAndSet(false, true)) giveBack(this, discarded);
        }
    }
}
