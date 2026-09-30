/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.storage.service;

import dev.chojo.ember.feature.storage.backend.StorageBackend;
import dev.chojo.ember.feature.storage.backend.StoredStream;
import dev.chojo.ember.feature.storage.migration.MigrationException;
import dev.chojo.ember.util.Sha256;

import java.io.IOException;
import java.util.List;

/**
 * Copies keys from one backend to another for a move. A key the target already holds with the same
 * SHA-256 is skipped, so a move that crashed can be run again and picks up where it stopped.
 */
final class BackendCopy {
    /** One in this many moved keys is checked on the target afterwards. */
    private static final int SAMPLE_DENOMINATOR = 100;

    private BackendCopy() {}

    static Stats copyAll(StorageBackend source, StorageBackend target, List<String> keys) {
        int copied = 0;
        int skipped = 0;
        long bytes = 0;
        for (String key : keys) {
            if (target.exists(key) && hashOf(target, key).equals(hashOf(source, key))) {
                skipped++;
                continue;
            }
            bytes += copyOne(source, target, key);
            copied++;
        }
        return new Stats(keys.size(), copied, skipped, bytes);
    }

    /** Checks that an even sample of the moved keys arrived on the target. */
    static void sampleVerify(StorageBackend target, List<String> keys) {
        if (keys.isEmpty()) return;
        int sampleSize = Math.max(1, keys.size() / SAMPLE_DENOMINATOR);
        int step = Math.max(1, keys.size() / sampleSize);
        for (int i = 0; i < keys.size() && i < sampleSize * step; i += step) {
            String key = keys.get(i);
            if (!target.exists(key)) {
                throw new MigrationException("Sample verification failed: target missing key " + key);
            }
        }
    }

    private static long copyOne(StorageBackend source, StorageBackend target, String key) {
        try (StoredStream stream = source.read(key)
                .orElseThrow(() -> new MigrationException("Source key disappeared mid-migration: " + key))) {
            long length = stream.contentLength();
            target.store(key, stream.body(), length, stream.metadata());
            return length;
        } catch (IOException e) {
            throw new MigrationException("Failed to read source key " + key, e);
        }
    }

    private static String hashOf(StorageBackend backend, String key) {
        try (StoredStream stream = backend.read(key).orElseThrow(() -> new MigrationException("Missing key: " + key))) {
            String stored = stream.metadata().sha256();
            if (stored != null && !stored.isBlank()) return stored;
            return Sha256.hex(stream.body());
        } catch (IOException e) {
            throw new MigrationException("Failed to read key for verification " + key, e);
        }
    }

    record Stats(int total, int copied, int skipped, long bytes) {
        static final Stats NONE = new Stats(0, 0, 0, 0L);

        Stats plus(Stats other) {
            return new Stats(total + other.total, copied + other.copied, skipped + other.skipped, bytes + other.bytes);
        }
    }
}
