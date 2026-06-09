/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

/**
 * Converts presentation files (PPTX, PPT, ODP) to PDF using LibreOffice in headless mode.
 * The LibreOffice binary path can be configured via the {@code LIBREOFFICE_BIN} environment variable.
 */
public final class PresentationConverter {
    private static final Logger log = LoggerFactory.getLogger(PresentationConverter.class);
    private static final String LIBREOFFICE_BIN = System.getenv().getOrDefault("LIBREOFFICE_BIN", "libreoffice");

    private PresentationConverter() {}

    /**
     * Converts a presentation file to PDF.
     *
     * @param data     raw file bytes of the presentation
     * @param filename original filename (used to determine extension)
     * @return the converted PDF bytes
     * @throws IOException if conversion fails
     */
    public static byte[] toPdf(byte[] data, String filename) throws IOException {
        Path tempDir = Files.createTempDirectory("presentation-convert-");
        try {
            String extension = getExtension(filename);
            Path inputFile = tempDir.resolve("input." + extension);
            Files.write(inputFile, data);

            var process = new ProcessBuilder(
                            LIBREOFFICE_BIN,
                            "--headless",
                            "--convert-to",
                            "pdf",
                            "--outdir",
                            tempDir.toString(),
                            inputFile.toString())
                    .redirectErrorStream(true)
                    .start();

            String processOutput = new String(process.getInputStream().readAllBytes());
            int exitCode = process.waitFor();

            if (exitCode != 0) {
                log.error("LibreOffice conversion failed (exit {}): {}", exitCode, processOutput);
                throw new IOException("Presentation conversion failed: " + processOutput);
            }

            Path pdfFile = tempDir.resolve("input.pdf");
            if (!Files.exists(pdfFile)) {
                throw new IOException("LibreOffice did not produce a PDF output");
            }

            return Files.readAllBytes(pdfFile);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Presentation conversion interrupted", e);
        } finally {
            cleanup(tempDir);
        }
    }

    private static String getExtension(String filename) {
        if (filename != null) {
            int dot = filename.lastIndexOf('.');
            if (dot > 0) return filename.substring(dot + 1).toLowerCase();
        }
        return "pptx";
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
