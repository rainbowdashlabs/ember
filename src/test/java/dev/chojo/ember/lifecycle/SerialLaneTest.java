/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.lifecycle;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SerialLaneTest {
    private final TaskScheduler scheduler = new TaskScheduler();

    @AfterEach
    void stop() {
        scheduler.stop(Duration.ofSeconds(1));
    }

    @Test
    void runsItemsOneAtATimeInOrder() throws InterruptedException {
        var lane = scheduler.lane("ordered");
        var order = new CopyOnWriteArrayList<Integer>();
        var concurrent = new AtomicInteger();
        var overlapped = new AtomicBoolean();
        var done = new CountDownLatch(20);

        IntStream.range(0, 20)
                .forEach(i -> lane.submit(() -> {
                    if (concurrent.incrementAndGet() > 1) overlapped.set(true);
                    order.add(i);
                    concurrent.decrementAndGet();
                    done.countDown();
                }));

        assertTrue(done.await(2, TimeUnit.SECONDS));
        assertFalse(overlapped.get(), "two items of a lane must never run at once");
        assertEquals(IntStream.range(0, 20).boxed().toList(), List.copyOf(order));
    }

    @Test
    void fullLaneRefusesMoreWork() throws InterruptedException {
        var lane = scheduler.lane("bounded", 1);
        var release = new CountDownLatch(1);
        var started = new CountDownLatch(1);
        lane.submit(() -> {
            started.countDown();
            try {
                release.await(1, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        assertTrue(started.await(1, TimeUnit.SECONDS));

        assertTrue(lane.submit(() -> {}), "one item may wait");
        assertFalse(lane.submit(() -> {}), "a second waiting item is refused");
        release.countDown();
    }

    @Test
    void failingItemDoesNotHoldUpTheRest() throws InterruptedException {
        var lane = scheduler.lane("failing");
        var ran = new CountDownLatch(1);

        lane.submit(() -> {
            throw new IllegalStateException("expected by the test");
        });
        lane.submit(ran::countDown);

        assertTrue(ran.await(1, TimeUnit.SECONDS));
    }

    @Test
    void laneStartsAgainAfterRunningEmpty() throws InterruptedException {
        var lane = scheduler.lane("again");
        var first = new CountDownLatch(1);
        lane.submit(first::countDown);
        assertTrue(first.await(1, TimeUnit.SECONDS));
        Thread.sleep(20);

        var second = new CountDownLatch(1);
        lane.submit(second::countDown);

        assertTrue(second.await(1, TimeUnit.SECONDS));
    }

    @Test
    void queuedItemsAreAbandonedOnStop() throws InterruptedException {
        var lane = scheduler.lane("abandoned");
        var started = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var behind = new AtomicBoolean();

        lane.submit(() -> {
            started.countDown();
            try {
                release.await(1, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        lane.submit(() -> behind.set(true));
        assertTrue(started.await(1, TimeUnit.SECONDS));

        var stopper = Thread.ofVirtual().start(() -> scheduler.stop(Duration.ofSeconds(1)));
        while (!scheduler.isStopping()) {
            Thread.onSpinWait();
        }
        release.countDown();
        stopper.join();

        assertFalse(lane.submit(() -> behind.set(true)), "a stopped lane must refuse new items");
        assertFalse(behind.get(), "items still queued at stop must not run");
    }
}
