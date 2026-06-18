/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

import javax.imageio.ImageIO;

/**
 * Encodes a {@link BufferedImage} into WebP bytes by shelling out to the {@code cwebp}
 * binary. Mirrors the {@code TYPST_BIN}/{@code PANDOC_BIN} pattern: the binary path can be
 * overridden with the {@code CWEBP_BIN} environment variable; the default {@code cwebp} is
 * resolved on {@code PATH}.
 *
 * <p>When {@code cwebp} is unavailable on the host (e.g. operator did not install
 * {@code libwebp}), {@link #isAvailable()} returns {@code false} and callers should skip WebP
 * variant generation — see {@code PageImageVariantService}.
 */
public final class WebpEncoder {
    private static final Logger log = LoggerFactory.getLogger(WebpEncoder.class);
    private static final String CWEBP_BIN = System.getenv().getOrDefault("CWEBP_BIN", "cwebp");
    private static volatile Boolean availabilityCache;

    private WebpEncoder() {}

    /**
     * Returns whether the configured {@code cwebp} binary can be invoked. Result is cached
     * after the first probe; restart the JVM to pick up an installed binary.
     */
    public static boolean isAvailable() {
        Boolean cached = availabilityCache;
        if (cached != null) return cached;
        synchronized (WebpEncoder.class) {
            if (availabilityCache != null) return availabilityCache;
            try {
                var process = new ProcessBuilder(CWEBP_BIN, "-version")
                        .redirectErrorStream(true)
                        .start();
                int exit = process.waitFor();
                availabilityCache = exit == 0;
            } catch (IOException | InterruptedException e) {
                if (e instanceof InterruptedException) Thread.currentThread().interrupt();
                log.info("cwebp not available on PATH (CWEBP_BIN={}); WebP variants will be skipped.", CWEBP_BIN);
                availabilityCache = false;
            }
            return availabilityCache;
        }
    }

    /**
     * Encodes the given image as WebP at the supplied quality (0–100, default 80 mirrors
     * {@code cwebp}'s own default). Throws {@link IOException} on encode failure — callers
     * that treat WebP as best-effort should catch and log.
     */
    public static byte[] encode(BufferedImage image, int quality) throws IOException, InterruptedException {
        if (!isAvailable()) {
            throw new IOException("cwebp binary not available");
        }
        Path workDir = Files.createTempDirectory("ember-webp-");
        try {
            Path input = workDir.resolve("input.png");
            Path output = workDir.resolve("output.webp");
            try (var os = Files.newOutputStream(input)) {
                ImageIO.write(image, "png", os);
            }
            int clamped = Math.clamp(quality, 0, 100);
            var process = new ProcessBuilder(
                            CWEBP_BIN,
                            "-quiet",
                            "-q",
                            String.valueOf(clamped),
                            input.toString(),
                            "-o",
                            output.toString())
                    .redirectErrorStream(true)
                    .start();
            String log = new String(process.getInputStream().readAllBytes());
            int exit = process.waitFor();
            if (exit != 0) {
                throw new IOException("cwebp failed (exit " + exit + "): " + log);
            }
            return Files.readAllBytes(output);
        } finally {
            cleanup(workDir);
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
}
