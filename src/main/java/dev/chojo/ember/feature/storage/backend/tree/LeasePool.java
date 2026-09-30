/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.storage.backend.tree;

import dev.chojo.ember.feature.storage.backend.StorageUnavailableException;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A bounded pool of protocol sessions for one backend.
 *
 * <p>At most {@code maxSize} trees are lent at once; a caller beyond that waits for one to come back,
 * at most {@code acquireTimeout}, and is then refused with {@link StorageUnavailableException}.
 * Trees are opened on demand and kept for the next caller. A tree that was discarded, or whose
 * connection is gone, is closed on return rather than lent again.
 *
 * @param <T> the kind of tree
 */
public final class LeasePool<T extends FileTree> implements TreeSource<T> {
    private final String name;
    private final Opener<T> opener;
    private final Duration acquireTimeout;
    private final Semaphore permits;
    private final Deque<T> idle = new ArrayDeque<>();
    private volatile boolean closed;

    /**
     * A pool that opens trees with {@code opener}.
     *
     * @param name           what the pool is for, in the messages it refuses with
     * @param opener         opens one tree
     * @param maxSize        how many trees may be lent at once
     * @param acquireTimeout how long a caller waits for a tree
     */
    public LeasePool(String name, Opener<T> opener, int maxSize, Duration acquireTimeout) {
        this.name = name;
        this.opener = opener;
        this.acquireTimeout = acquireTimeout;
        this.permits = new Semaphore(maxSize, true);
    }

    @Override
    public Lease<T> acquire() {
        if (closed) throw new StorageUnavailableException(name + " has been closed");
        waitForPermit();
        try {
            return new PooledLease(idleOrOpen());
        } catch (IOException | RuntimeException e) {
            permits.release();
            throw new StorageUnavailableException(name + " cannot be reached: " + e.getMessage(), e);
        }
    }

    @Override
    public void close() {
        closed = true;
        synchronized (idle) {
            idle.forEach(FileTree::close);
            idle.clear();
        }
    }

    private void waitForPermit() {
        try {
            if (!permits.tryAcquire(acquireTimeout.toMillis(), TimeUnit.MILLISECONDS)) {
                throw new StorageUnavailableException(name + " is busy, no connection came free in time");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new StorageUnavailableException(name + " was interrupted while waiting for a connection", e);
        }
    }

    private T idleOrOpen() throws IOException {
        synchronized (idle) {
            while (!idle.isEmpty()) {
                T tree = idle.pop();
                if (tree.isUsable()) return tree;
                tree.close();
            }
        }
        return opener.open();
    }

    private void giveBack(T tree, boolean discarded) {
        try {
            if (discarded || closed || !tree.isUsable()) {
                tree.close();
                return;
            }
            synchronized (idle) {
                idle.push(tree);
            }
        } finally {
            permits.release();
        }
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
            if (returned.compareAndSet(false, true)) giveBack(tree, discarded);
        }
    }
}
