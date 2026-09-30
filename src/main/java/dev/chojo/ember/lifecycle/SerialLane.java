/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.lifecycle;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;
import java.util.Queue;

/**
 * An ordered queue of work that runs one item at a time, in the order the items were handed in.
 *
 * <p>Takes the place of a single-thread executor held for the life of the process. Nothing waits while the
 * lane is empty: the first item handed to an idle lane starts a virtual thread that drains the queue and
 * ends with it. Each item is guarded on its own, so one that fails does not hold up the ones behind it.
 * Once the scheduler stops, the item in hand may finish within the grace period and the rest are abandoned.
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
        if (scheduler.submit(name, this::drain)) return true;
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

    private synchronized Runnable poll() {
        if (scheduler.isStopping() && !queue.isEmpty()) {
            log.warn("Abandoning {} queued items of {}: the scheduler is stopping", queue.size(), name);
            queue.clear();
        }
        Runnable next = queue.poll();
        if (next == null) draining = false;
        return next;
    }
}
