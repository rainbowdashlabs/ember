/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.storage.backend.tree;

import dev.chojo.ember.feature.storage.backend.StorageUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * A bounded pool of protocol sessions for one backend.
 *
 * <p>At most {@code maxSize} trees are open at once. Trees are opened on demand and handed from one
 * caller to the next; a caller beyond the limit waits for one to come back, at most
 * {@code acquireTimeout}, and is then refused with {@link StorageUnavailableException}. A tree that
 * was discarded, or whose connection is gone, is closed on return rather than lent again. Idle trees
 * beyond the first are closed after {@link #IDLE_CLOSE}, so a quiet backend keeps one session and not
 * four. When a server refuses to open another tree while others it opened still work, the pool takes
 * that as the server's limit and stays below it.
 *
 * <p>A lease held longer than {@link #LEAK_AFTER} is reported once, with the stack of whoever took it:
 * a caller that never closes what it read would otherwise exhaust the pool without a trace.
 *
 * <p>Closing the pool closes the idle trees at once and every lent one as it comes back; a lease
 * still out after {@link #DRAIN_LIMIT} has its tree closed underneath it.
 *
 * @param <T> the kind of tree
 */
public final class LeasePool<T extends FileTree> implements TreeSource<T> {
    /** How long an idle tree beyond the first is kept. */
    public static final Duration IDLE_CLOSE = Duration.ofMinutes(5);

    /** How long a lease may be held before it is reported. */
    public static final Duration LEAK_AFTER = Duration.ofMinutes(10);

    /** How long closing the pool waits for lent trees to come back. */
    public static final Duration DRAIN_LIMIT = Duration.ofSeconds(30);

    private static final Logger log = LoggerFactory.getLogger(LeasePool.class);

    private final String name;
    private final Opener<T> opener;
    private final Duration acquireTimeout;
    private final Clock clock;
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition changed = lock.newCondition();
    private final Deque<Idle<T>> idle = new ArrayDeque<>();
    private final Map<PooledLease, Lent> lent = new IdentityHashMap<>();
    private int open;
    private int limit;
    private boolean closed;

    /**
     * A pool that opens trees with {@code opener}.
     *
     * @param name           what the pool is for, in the messages it refuses with
     * @param opener         opens one tree
     * @param maxSize        how many trees may be open at once
     * @param acquireTimeout how long a caller waits for a tree
     */
    public LeasePool(String name, Opener<T> opener, int maxSize, Duration acquireTimeout) {
        this(name, opener, maxSize, acquireTimeout, Clock.systemUTC());
    }

    LeasePool(String name, Opener<T> opener, int maxSize, Duration acquireTimeout, Clock clock) {
        this.name = name;
        this.opener = opener;
        this.acquireTimeout = acquireTimeout;
        this.clock = clock;
        this.limit = maxSize;
    }

    @Override
    public Lease<T> acquire() {
        long deadline = System.nanoTime() + acquireTimeout.toNanos();
        while (true) {
            var ready = idleOrSlot(deadline);
            if (ready != null) return lend(ready);
            try {
                return lend(opener.open());
            } catch (IOException | RuntimeException e) {
                if (!openFailedBelowTheServersLimit()) {
                    throw new StorageUnavailableException(name + " cannot be reached: " + e.getMessage(), e);
                }
                log.warn("{} refused another connection, staying at {} from now on", name, limit, e);
            }
        }
    }

    /** How many trees are open, lent or idle. */
    public int openTrees() {
        lock.lock();
        try {
            return open;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void close() {
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
        if (drain) Thread.ofVirtual().name("storage-drain").start(this::closeStragglers);
    }

    /**
     * An idle tree for the caller, or null when the caller may open one. Waits while every tree is
     * lent and none may be opened.
     */
    private T idleOrSlot(long deadline) {
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
     * Gives the slot of a failed open back and says whether it was the server's limit rather than an
     * outage: another tree it opened still works, so it can be reached and simply wants fewer.
     */
    private boolean openFailedBelowTheServersLimit() {
        lock.lock();
        try {
            open--;
            changed.signalAll();
            boolean othersWork = idle.stream().anyMatch(entry -> entry.tree().isUsable())
                    || lent.keySet().stream().anyMatch(lease -> lease.tree.isUsable());
            if (!othersWork || open == 0) return false;
            limit = Math.max(1, open);
            return true;
        } finally {
            lock.unlock();
        }
    }

    private Lease<T> lend(T tree) {
        var lease = new PooledLease(tree);
        lock.lock();
        try {
            lent.put(lease, new Lent(clock.instant(), new Throwable("Lease of " + name + " taken here")));
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
        for (Lent held : lent.values()) {
            if (!held.reported && held.since().isBefore(cutoff)) {
                held.reported = true;
                log.warn(
                        "A connection of {} has been held since {} and was never given back",
                        name,
                        held.since(),
                        held.taker());
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
            lent.keySet().forEach(lease -> stragglers.add(lease.tree));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            lock.unlock();
        }
        if (!stragglers.isEmpty()) log.warn("{} closed {} connection(s) still in use", name, stragglers.size());
        stragglers.forEach(FileTree::close);
    }

    /**
     * Opens one tree of the pool.
     *
     * @param <T> the kind of tree
     */
    @FunctionalInterface
    public interface Opener<T extends FileTree> {
        /**
         * Opens a tree, connecting and signing in as far as needed.
         *
         * @return the tree
         * @throws IOException when the server cannot be reached or refuses the sign-in
         */
        T open() throws IOException;
    }

    private record Idle<T>(T tree, Instant since) {}

    private static final class Lent {
        private final Instant since;
        private final Throwable taker;
        private boolean reported;

        private Lent(Instant since, Throwable taker) {
            this.since = since;
            this.taker = taker;
        }

        Instant since() {
            return since;
        }

        Throwable taker() {
            return taker;
        }
    }

    private final class PooledLease implements Lease<T> {
        private final T tree;
        private final AtomicBoolean returned = new AtomicBoolean();
        private volatile boolean discarded;

        private PooledLease(T tree) {
            this.tree = tree;
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
