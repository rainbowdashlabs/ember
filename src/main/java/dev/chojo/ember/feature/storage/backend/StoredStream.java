/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.storage.backend;

import java.io.IOException;
import java.io.InputStream;

/**
 * Streaming read result from a {@link StorageBackend}. The carried {@link #body()} is the
 * raw protocol response stream (no intermediate buffer); the caller is expected to either
 * pipe it straight into a Javalin response or fully drain it. The {@code AutoCloseable}
 * contract closes the underlying stream, which on remote backends releases the connection
 * back to the pool.
 */
public record StoredStream(InputStream body, long contentLength, ObjectMetadata metadata) implements AutoCloseable {

    @Override
    public void close() throws IOException {
        body.close();
    }
}
