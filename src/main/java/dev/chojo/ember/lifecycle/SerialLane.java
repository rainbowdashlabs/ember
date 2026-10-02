/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.lifecycle;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;
import java.util.Queue;

/**
 * A queue of work that runs one item at a time, in the order handed in, without holding a thread while empty:
 * the first item for an idle lane starts a virtual thread that drains the queue and ends with it.
 *
 * <p>Each item is guarded on its own, so a failure does not hold up the rest. Once the scheduler stops, the item
 * in hand may finish within the grace period and the queued ones are dropped.
 */
public final class SerialLane {
    private static final Logger log = LoggerFactory.getLogger(SerialLane.class);

    private final String name;
    private final int capacity;
    private final TaskScheduler scheduler;
    private final Queue<Runnable> queue = new ArrayDeque<>();
    private boolean draining;

    SerialLane(String name, int capacity, TaskScheduler scheduler) {
        this.name = name;
        this.capacity = capacity;
        this.scheduler = scheduler;
    }

    /**
     * Queues a piece of work behind everything already in the lane.
     *
     * @param work the work
     * @return {@code false} when the lane is full or the scheduler is stopping, and the work was not accepted
     */
    public boolean submit(Runnable work) {
        synchronized (this) {
            if (scheduler.isStopping() || queue.size() >= capacity) return false;
            queue.add(work);
            if (draining) return true;
            draining = true;
        }
        if (scheduler.background(name, this::drain)) return true;
        synchronized (this) {
            queue.clear();
            draining = false;
        }
        return false;
    }

    private void drain() {
        Runnable next;
        while ((next = poll()) != null) {
            TaskScheduler.runGuarded(name, next);
        }
    }

    private synchronized @Nullable Runnable poll() {
        if (scheduler.isStopping() && !queue.isEmpty()) {
            log.warn("Abandoning {} queued items of {}: the scheduler is stopping", queue.size(), name);
            queue.clear();
        }
        Runnable next = queue.poll();
        if (next == null) draining = false;
        return next;
    }
}
