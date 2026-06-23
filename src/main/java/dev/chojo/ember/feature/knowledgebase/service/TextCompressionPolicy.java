/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.knowledgebase.service;

import dev.chojo.ember.conf.file.elements.Storage;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Locale;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Decides whether a given {@code (contentType, bytes)} payload should be gzipped on disk
 * (concept §11.3) and performs the encoding / decoding when the answer is yes. Pulled out of
 * {@code KbFileStorageService} so the storage service stays a pure I/O layer that just stores
 * whatever bytes it is given.
 */
@Singleton
public class TextCompressionPolicy {

    private final Storage storageConfig;

    @Inject
    public TextCompressionPolicy(Storage storageConfig) {
        this.storageConfig = storageConfig;
    }

    /**
     * Whether {@code contentType} qualifies for transparent on-disk gzip. Always {@code false}
     * when {@link Storage#compressTextFiles()} is off.
     */
    public boolean shouldGzip(String contentType) {
        if (!storageConfig.compressTextFiles() || contentType == null) return false;
        String lower = contentType.toLowerCase(Locale.ROOT);
        if (lower.startsWith("text/")) return true;
        return switch (lower) {
            case "application/json",
                    "application/xml",
                    "application/yaml",
                    "application/x-yaml",
                    "application/javascript",
                    "application/x-www-form-urlencoded",
                    "image/svg+xml" -> true;
            default -> false;
        };
    }

    /**
     * GZIP-encodes {@code data}; returns a fresh byte array.
     */
    public byte[] gzip(byte[] data) {
        try {
            var out = new ByteArrayOutputStream(Math.max(64, data.length / 3));
            try (var gzip = new GZIPOutputStream(out)) {
                gzip.write(data);
            }
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("gzip failed", e);
        }
    }

    /**
     * GZIP-decodes {@code data}; returns the decompressed payload.
     */
    public byte[] gunzip(byte[] data) {
        try (var gzip = new GZIPInputStream(new ByteArrayInputStream(data))) {
            return gzip.readAllBytes();
        } catch (IOException e) {
            throw new IllegalStateException("gunzip failed", e);
        }
    }
}
