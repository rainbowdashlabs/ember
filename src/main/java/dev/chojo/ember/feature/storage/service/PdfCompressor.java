/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.storage.service;

import dev.chojo.ember.conf.file.elements.Storage;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

/**
 * Lossless PDF recompression via {@code qpdf --linearize --object-streams=generate}. Mirrors
 * the {@link PresentationCompressor} contract — {@link #shouldCompress(String, long)} is the
 * single gate; {@link #compress(byte[])} returns the smaller of (recompressed, original).
 *
 * <p>qpdf is invoked through {@link ProcessBuilder} with the {@code QPDF_BIN} environment
 * override (default {@code qpdf} on PATH). When the binary is missing or fails, the original
 * bytes are returned unchanged — compression is opportunistic.
 *
 * <p>Typical savings: 5–25% depending on how the producer wrote object streams. Linearization
 * also lets PDF readers stream the first page before the full file lands, which is a UX win
 * for large documents served over slow links.
 */
@Singleton
public class PdfCompressor {
    private static final Logger log = LoggerFactory.getLogger(PdfCompressor.class);
    private static final String QPDF_BIN = System.getenv().getOrDefault("QPDF_BIN", "qpdf");
    private static volatile Boolean availabilityCache;

    private final Storage storageConfig;

    @Inject
    public PdfCompressor(Storage storageConfig) {
        this.storageConfig = storageConfig;
    }

    /**
     * Returns whether the configured {@code qpdf} binary can be invoked. Result is cached
     * after the first probe; restart the JVM to pick up a freshly installed binary.
     */
    public static boolean isAvailable() {
        Boolean cached = availabilityCache;
        if (cached != null) return cached;
        synchronized (PdfCompressor.class) {
            if (availabilityCache != null) return availabilityCache;
            try {
                var process = new ProcessBuilder(QPDF_BIN, "--version")
                        .redirectErrorStream(true)
                        .start();
                int exit = process.waitFor();
                availabilityCache = exit == 0;
            } catch (IOException | InterruptedException e) {
                if (e instanceof InterruptedException) Thread.currentThread().interrupt();
                log.info("qpdf not available on PATH (QPDF_BIN={}); PDF compression will be skipped.", QPDF_BIN);
                availabilityCache = false;
            }
            return availabilityCache;
        }
    }

    /**
     * Resets the cached availability probe. Used by tests that toggle the binary path.
     */
    public static void resetAvailabilityCacheForTests() {
        availabilityCache = null;
    }

    private static void cleanup(Path dir) {
        try (var walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                }
            });
        } catch (IOException ignored) {
        }
    }

    /**
     * Returns {@code true} when the file is a PDF, larger than the configured threshold, the
     * config toggle is on, and {@code qpdf} is available. The toggle-on-but-no-binary case
     * returns {@code false} so callers fall back to storing the original without an exception.
     */
    public boolean shouldCompress(String mimeType, long fileSize) {
        return storageConfig.compressPdfs()
                && "application/pdf".equalsIgnoreCase(mimeType)
                && fileSize > storageConfig.compressThresholdBytes()
                && isAvailable();
    }

    /**
     * Recompresses the supplied PDF bytes. Returns the smaller of (recompressed, original).
     * Failures are logged at warn and never propagated — the upload itself should never be
     * aborted because qpdf had a bad day.
     */
    public byte[] compress(byte[] data) {
        if (!isAvailable()) return data;
        Path workDir;
        try {
            workDir = Files.createTempDirectory("ember-qpdf-");
        } catch (IOException e) {
            log.warn("Failed to create temp dir for qpdf, using original", e);
            return data;
        }
        try {
            Path input = workDir.resolve("input.pdf");
            Path output = workDir.resolve("output.pdf");
            Files.write(input, data);
            var process = new ProcessBuilder(
                            QPDF_BIN,
                            "--linearize",
                            "--object-streams=generate",
                            "--compress-streams=y",
                            input.toString(),
                            output.toString())
                    .redirectErrorStream(true)
                    .start();
            String tail = new String(process.getInputStream().readAllBytes());
            int exit = process.waitFor();
            if (exit != 0 && exit != 3) {
                log.warn("qpdf exited {} (output: {}); using original", exit, tail);
                return data;
            }
            byte[] recompressed = Files.readAllBytes(output);
            if (recompressed.length < data.length) {
                long saved = data.length - recompressed.length;
                log.info(
                        "PDF compressed: {} -> {} (saved {} bytes, {}%)",
                        data.length, recompressed.length, saved, String.format("%.1f", saved * 100.0 / data.length));
                return recompressed;
            }
            return data;
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            log.warn("Failed to recompress PDF, using original", e);
            return data;
        } finally {
            cleanup(workDir);
        }
    }
}
