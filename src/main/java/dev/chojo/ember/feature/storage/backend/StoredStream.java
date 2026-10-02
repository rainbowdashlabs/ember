/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.storage.backend;

import java.io.IOException;
import java.io.InputStream;

/**
 * An object opened by {@link StorageBackend#read}, streamed straight from the protocol. Closing it
 * gives the connection back to the pool, so every caller has to.
 */
public record StoredStream(InputStream body, long contentLength, ObjectMetadata metadata) implements AutoCloseable {

    @Override
    public void close() throws IOException {
        body.close();
    }
}
