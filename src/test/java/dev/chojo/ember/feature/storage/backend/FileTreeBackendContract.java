/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.storage.backend;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The byte layout every tree-shaped backend keeps, on top of the contract all backends share.
 *
 * <p>The layout is what makes a store written by one build readable by another and lets a migration
 * copy bytes between backends: the object under its key, its metadata beside it as
 * {@code <key>.meta.json}, and nothing else left behind. A subclass writes and reads the raw files
 * through the protocol itself, around the backend, to check exactly that.
 */
public abstract class FileTreeBackendContract extends StorageBackendContract {
    private static final String LEGACY_SIDECAR =
            "{\"contentType\":\"image/png\",\"sha256\":\"abc123\",\"originalFilename\":\"a.png\",\"contentEncoding\":\"\"}";

    /**
     * Writes a file past the backend, the way an earlier build left it.
     *
     * @param key   the key, relative to the backend's base path
     * @param bytes what the file holds
     */
    protected abstract void writeRaw(String key, byte[] bytes) throws IOException;

    /**
     * Reads a file past the backend.
     *
     * @param key the key, relative to the backend's base path
     * @return what the file holds, or empty when it does not exist
     */
    protected abstract Optional<byte[]> readRaw(String key) throws IOException;

    /**
     * The container the backend's server runs in, for the tests that take it away for a while.
     *
     * @return the container, or empty for a store that has no server
     */
    protected Optional<GenericContainer<?>> server() {
        return Optional.empty();
    }

    /**
     * A server that stops answering ends a call as unreachable within the timeouts, rather than as a
     * fault or never, and the first call once it is back succeeds on a fresh connection.
     */
    @Test
    void aCallDuringAnOutageEndsAsUnavailableAndTheFirstCallAfterItSucceeds() {
        var server = server();
        Assumptions.assumeTrue(server.isPresent(), "a store without a server has no outage");
        store("scope/outage/key", "before");
        var docker = DockerClientFactory.instance().client();
        String id = server.get().getContainerId();

        docker.pauseContainerCmd(id).exec();
        long started = System.nanoTime();
        try {
            assertThrows(StorageUnavailableException.class, () -> backend().exists("scope/outage/key"));
        } finally {
            docker.unpauseContainerCmd(id).exec();
        }
        long seconds = Duration.ofNanos(System.nanoTime() - started).toSeconds();

        assertTrue(seconds < 100, "the outage was reported after " + seconds + " s");
        assertTrue(backend().exists("scope/outage/key"));
    }

    @Test
    void anObjectAnEarlierBuildStoredIsReadAsItWas() throws IOException {
        byte[] body = "legacy".getBytes(StandardCharsets.UTF_8);
        writeRaw("scope/legacy/key", body);
        writeRaw("scope/legacy/key.meta.json", LEGACY_SIDECAR.getBytes(StandardCharsets.UTF_8));

        try (var stream = backend().read("scope/legacy/key").orElseThrow()) {
            assertArrayEquals(body, stream.body().readAllBytes());
            assertEquals("image/png", stream.metadata().contentType());
            assertEquals("abc123", stream.metadata().sha256());
            assertEquals("a.png", stream.metadata().originalFilename().orElseThrow());
        }
        assertEquals(List.of("scope/legacy/key"), backend().listByPrefix("scope/legacy"));
    }

    /**
     * Readers are not queued behind an upload. The store runs slowly on purpose, a 20 MB body trickling
     * in over several seconds, and 32 reads started meanwhile have to be done before it is: with every
     * call of a backend behind one lock, as it was, each of them waited for the whole upload.
     */
    @Test
    void readsAreServedWhileALargeStoreRuns() throws Exception {
        store("scope/busy/small", "small");
        var storeDone = new AtomicLong();
        var executor = Executors.newVirtualThreadPerTaskExecutor();
        try (executor) {
            var upload = executor.submit(() -> {
                backend()
                        .store(
                                "scope/busy/large",
                                new SlowStream(20 * 1024 * 1024, 4_000),
                                20L * 1024 * 1024,
                                ObjectMetadata.of("application/octet-stream"));
                storeDone.set(System.nanoTime());
            });
            Thread.sleep(300);
            long started = System.nanoTime();
            var reads = new ArrayList<Future<Long>>();
            for (int i = 0; i < 32; i++) {
                reads.add(executor.submit(() -> {
                    try (var stream = backend().read("scope/busy/small").orElseThrow()) {
                        assertEquals("small", new String(stream.body().readAllBytes(), StandardCharsets.UTF_8));
                    }
                    return System.nanoTime();
                }));
            }
            long lastRead = 0;
            for (var read : reads) lastRead = Math.max(lastRead, read.get());
            upload.get();
            System.out.printf(
                    "%s: 32 reads beside a 20 MB store took %d ms%n",
                    getClass().getSimpleName(), (lastRead - started) / 1_000_000);
            assertTrue(lastRead < storeDone.get(), "every read finished before the upload");
        }
    }

    @Test
    void whatIsStoredLiesWhereAnEarlierBuildLooksForIt() throws IOException {
        byte[] body = "fresh".getBytes(StandardCharsets.UTF_8);

        var sealed = backend()
                .storeSealed(
                        "scope/fresh/key",
                        new ByteArrayInputStream(body),
                        body.length,
                        ObjectMetadata.of("text/plain"));

        assertArrayEquals(body, readRaw("scope/fresh/key").orElseThrow());
        String sidecar = new String(readRaw("scope/fresh/key.meta.json").orElseThrow(), StandardCharsets.UTF_8);
        assertTrue(sidecar.contains("\"contentType\":\"text/plain\""), sidecar);
        assertTrue(sidecar.contains("\"sha256\":\"" + sealed.sha256() + "\""), sidecar);
        assertEquals(64, sealed.sha256().length());
    }

    /** A body that delivers its bytes evenly over a stretch of time, standing in for a slow client. */
    private static final class SlowStream extends InputStream {
        private final long total;
        private final long nanosPerByte;
        private final long start = System.nanoTime();
        private long sent;

        private SlowStream(long total, long millis) {
            this.total = total;
            this.nanosPerByte = millis * 1_000_000 / total;
        }

        @Override
        public int read() throws IOException {
            byte[] one = new byte[1];
            return read(one, 0, 1) < 0 ? -1 : one[0] & 0xFF;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            if (sent >= total) return -1;
            long due = start + sent * nanosPerByte;
            long wait = due - System.nanoTime();
            if (wait > 0) {
                try {
                    Thread.sleep(wait / 1_000_000, (int) (wait % 1_000_000));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IOException(e);
                }
            }
            int n = (int) Math.min(len, Math.min(64 * 1024, total - sent));
            Arrays.fill(b, off, off + n, (byte) 7);
            sent += n;
            return n;
        }
    }
}
