/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.storage.backend.tree;

import dev.chojo.ember.feature.storage.backend.StorageUnavailableException;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LeasePoolTest {
    private final MovableClock clock = new MovableClock();
    private final List<FakeTree> opened = new ArrayList<>();

    private LeasePool<FakeTree> pool(int size, Duration wait) {
        return new LeasePool<>("test storage", this::open, size, wait, clock);
    }

    private FakeTree open() {
        var tree = new FakeTree();
        opened.add(tree);
        return tree;
    }

    @Test
    void aReturnedTreeIsLentToTheNextCaller() {
        var pool = pool(4, Duration.ofSeconds(1));

        var first = pool.acquire();
        FakeTree tree = first.tree();
        first.close();
        first.close();

        try (var second = pool.acquire()) {
            assertSame(tree, second.tree());
        }
        assertEquals(1, pool.openTrees());
    }

    @Test
    void noMoreThanTheLimitAreLentAndTheNextCallerIsRefusedInTime() {
        var pool = pool(2, Duration.ofMillis(200));
        var a = pool.acquire();
        var b = pool.acquire();

        var refused = assertThrows(StorageUnavailableException.class, pool::acquire);

        assertTrue(refused.getMessage().contains("busy"));
        assertEquals(2, opened.size());
        a.close();
        b.close();
    }

    @Test
    void aWaitingCallerGetsTheTreeThatComesBack() throws Exception {
        var pool = pool(1, Duration.ofSeconds(5));
        var held = pool.acquire();

        var waiting = CompletableFuture.supplyAsync(() -> pool.acquire().tree());
        Thread.sleep(100);
        held.close();

        assertSame(held.tree(), waiting.get(5, TimeUnit.SECONDS));
    }

    @Test
    void aDiscardedOrBrokenTreeIsClosedAndReplaced() {
        var pool = pool(1, Duration.ofSeconds(1));
        var discarded = pool.acquire();
        discarded.discard();
        discarded.close();

        var broken = pool.acquire();
        broken.tree().usable = false;
        broken.close();

        try (var fresh = pool.acquire()) {
            assertTrue(opened.get(0).closed);
            assertTrue(opened.get(1).closed);
            assertNotSame(broken.tree(), fresh.tree());
        }
    }

    @Test
    void anIdleTreeThatBrokeIsNotLentAgain() {
        var pool = pool(1, Duration.ofSeconds(1));
        var lease = pool.acquire();
        lease.close();
        lease.tree().usable = false;

        try (var next = pool.acquire()) {
            assertNotSame(lease.tree(), next.tree());
            assertTrue(lease.tree().closed);
        }
    }

    @Test
    void idleTreesBeyondOneCloseAfterFiveMinutes() {
        var pool = pool(4, Duration.ofSeconds(1));
        var a = pool.acquire();
        var b = pool.acquire();
        var c = pool.acquire();
        a.close();
        b.close();
        c.close();

        clock.advance(LeasePool.IDLE_CLOSE.plusSeconds(1));
        pool.acquire().close();

        assertEquals(1, pool.openTrees());
    }

    @Test
    void aLeaseHeldTooLongIsReportedAndStillServed() {
        var pool = pool(2, Duration.ofSeconds(1));
        var forgotten = pool.acquire();
        clock.advance(LeasePool.LEAK_AFTER.plusMinutes(1));

        pool.acquire().close();
        pool.acquire().close();

        assertFalse(forgotten.tree().closed);
    }

    @Test
    void aServerThatRefusesAnotherConnectionIsTakenAtItsWord() {
        var refusing = new LeasePool<FakeTree>(
                "test storage",
                () -> {
                    if (opened.size() >= 2) throw new IOException("too many sessions");
                    return open();
                },
                4,
                Duration.ofMillis(300),
                clock);
        var a = refusing.acquire();
        var b = refusing.acquire();

        assertThrows(StorageUnavailableException.class, refusing::acquire);
        a.close();

        try (var again = refusing.acquire()) {
            assertSame(a.tree(), again.tree());
        }
        b.close();
    }

    @Test
    void aServerThatCannotBeReachedIsRefusedAtOnce() {
        var down = new LeasePool<FakeTree>(
                "test storage",
                () -> {
                    throw new IOException("connection refused");
                },
                4,
                Duration.ofSeconds(5),
                clock);

        var refused = assertThrows(StorageUnavailableException.class, down::acquire);

        assertTrue(refused.getMessage().contains("cannot be reached"));
    }

    @Test
    void closingClosesIdleTreesAtOnceAndLentOnesOnReturn() {
        var pool = pool(2, Duration.ofSeconds(1));
        var idle = pool.acquire();
        var lent = pool.acquire();
        idle.close();

        pool.close();
        pool.close();

        assertTrue(idle.tree().closed);
        assertFalse(lent.tree().closed);
        lent.close();
        assertTrue(lent.tree().closed);
        assertThrows(StorageUnavailableException.class, pool::acquire);
    }

    private static final class MovableClock extends Clock {
        private Instant now = Instant.parse("2026-09-30T08:00:00Z");

        void advance(Duration by) {
            now = now.plus(by);
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    private static final class FakeTree implements FileTree {
        private volatile boolean usable = true;
        private volatile boolean closed;

        @Override
        public OutputStream create(String path) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<OpenFile> open(String path) {
            return Optional.empty();
        }

        @Override
        public Optional<FileInfo> stat(String path) {
            return Optional.empty();
        }

        @Override
        public void replace(String source, String target) {}

        @Override
        public boolean remove(String path) {
            return false;
        }

        @Override
        public void makeDirectories(String path) {}

        @Override
        public List<FileInfo> list(String directory) {
            return List.of();
        }

        @Override
        public boolean removeDirectory(String path) {
            return false;
        }

        @Override
        public boolean isUsable() {
            return usable && !closed;
        }

        @Override
        public void close() {
            closed = true;
        }
    }
}
