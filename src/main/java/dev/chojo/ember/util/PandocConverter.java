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
import java.util.ArrayList;
import java.util.List;

/**
 * Converts documents between the formats pandoc understands.
 * The pandoc binary path can be configured via the {@code PANDOC_BIN} environment variable.
 */
public final class PandocConverter {
    private static final Logger log = LoggerFactory.getLogger(PandocConverter.class);
    private static final String PANDOC_BIN = System.getenv().getOrDefault("PANDOC_BIN", "pandoc");
    private static final Path STRIP_IMAGES_FILTER = Path.of("templates", "pandoc", "strip-images.lua");

    private PandocConverter() {}

    /**
     * Converts Markdown into a Typst markup fragment that can be embedded in a Typst document.
     *
     * <p>Images are replaced by their alternative text: they point at authenticated URLs that the
     * Typst compiler cannot fetch, and an unreachable image aborts the whole render.
     *
     * @param markdown the markdown source
     * @return the Typst markup fragment
     * @throws IOException if conversion fails
     */
    public static String markdownToTypst(String markdown) throws IOException {
        Path tempDir = Files.createTempDirectory("pandoc-typst-");
        try {
            Path inputFile = tempDir.resolve("input.md");
            Path outputFile = tempDir.resolve("output.typ");
            Files.writeString(inputFile, markdown);

            var command = new ArrayList<String>(List.of(PANDOC_BIN, "-f", "gfm", "-t", "typst", "--wrap=none"));
            if (Files.isRegularFile(STRIP_IMAGES_FILTER)) {
                command.add("--lua-filter=" + STRIP_IMAGES_FILTER);
            } else {
                log.warn("Pandoc image filter missing at {}; images may break the render", STRIP_IMAGES_FILTER);
            }
            command.addAll(List.of(inputFile.toString(), "-o", outputFile.toString()));

            var process = new ProcessBuilder(command).redirectErrorStream(true).start();
            String processOutput = new String(process.getInputStream().readAllBytes());
            int exitCode = process.waitFor();

            if (exitCode != 0) {
                log.error("Pandoc failed (exit {}): {}", exitCode, processOutput);
                throw new IOException("Pandoc conversion failed: " + processOutput);
            }

            return Files.readString(outputFile);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Pandoc conversion interrupted", e);
        } finally {
            cleanup(tempDir);
        }
    }

    private static void cleanup(Path tempDir) throws IOException {
        try (var stream = Files.list(tempDir)) {
            stream.forEach(file -> {
                try {
                    Files.deleteIfExists(file);
                } catch (IOException ignored) {
                }
            });
        }
        Files.deleteIfExists(tempDir);
    }

    /**
     * Converts a document to GitHub-Flavored Markdown.
     *
     * @param data       raw file bytes
     * @param fromFormat pandoc input format (e.g. "docx", "html", "odt")
     * @return the converted markdown text
     * @throws IOException if conversion fails
     */
    public static String toMarkdown(byte[] data, String fromFormat) throws IOException {
        Path tempDir = Files.createTempDirectory("pandoc-");
        try {
            Path inputFile = tempDir.resolve("input." + fromFormat);
            Path outputFile = tempDir.resolve("output.md");
            Files.write(inputFile, data);

            var process = new ProcessBuilder(
                            PANDOC_BIN,
                            "-f",
                            fromFormat,
                            "-t",
                            "gfm",
                            "--wrap=none",
                            inputFile.toString(),
                            "-o",
                            outputFile.toString())
                    .redirectErrorStream(true)
                    .start();

            String processOutput = new String(process.getInputStream().readAllBytes());
            int exitCode = process.waitFor();

            if (exitCode != 0) {
                log.error("Pandoc failed (exit {}): {}", exitCode, processOutput);
                throw new IOException("Pandoc conversion failed: " + processOutput);
            }

            return Files.readString(outputFile);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Pandoc conversion interrupted", e);
        } finally {
            cleanup(tempDir);
        }
    }
}
