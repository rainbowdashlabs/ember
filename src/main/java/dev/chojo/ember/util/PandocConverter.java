/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.util;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.zip.ZipInputStream;

/**
 * Converts documents between the formats pandoc understands.
 * The pandoc binary path can be configured via the {@code PANDOC_BIN} environment variable.
 */
public final class PandocConverter {
    private static final Logger log = LoggerFactory.getLogger(PandocConverter.class);
    private static final String PANDOC_BIN = System.getenv().getOrDefault("PANDOC_BIN", "pandoc");
    private static final Path PRINT_FILTER = Path.of("templates", "pandoc", "print-markdown.lua");

    /** What a ZIP archive starts with, which is what Word, OpenDocument and EPUB files are. */
    private static final byte[] ZIP = {0x50, 0x4B, 0x03, 0x04};

    /** What the compound file of the old binary office formats ({@code .doc}) starts with. */
    private static final byte[] COMPOUND_FILE = {(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0};

    private static final String ODT = "application/vnd.oasis.opendocument.text";
    private static final String EPUB = "application/epub+zip";

    /** More than any {@code mimetype} entry an OpenDocument or EPUB file carries. */
    private static final int MAX_MIMETYPE = 128;

    private PandocConverter() {}

    /**
     * Converts Markdown into a Typst markup fragment that can be embedded in a Typst document.
     *
     * <p>Only images whose source is a local name like {@code img-1.webp} survive, which is how a
     * caller hands over pictures it has placed next to the document. Every other image is replaced
     * by its alternative text: it points at a URL the Typst compiler cannot fetch, and an
     * unreachable image aborts the whole render. The formatting the article editor writes as HTML
     * (coloured text, highlights, underlining, a picture's caption) becomes its Typst equivalent.
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
            if (Files.isRegularFile(PRINT_FILTER)) {
                command.add("--lua-filter=" + PRINT_FILTER);
            } else {
                log.warn("Pandoc print filter missing at {}; images may break the render", PRINT_FILTER);
            }
            command.addAll(List.of(inputFile.toString(), "-o", outputFile.toString()));

            var process = new ProcessBuilder(command).redirectErrorStream(true).start();
            String processOutput = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
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
     * A document as markdown, with the pictures of its body.
     *
     * @param markdown the body, its pictures pointing at the paths of {@code media}
     * @param media    the pictures by the path the markdown names them with
     */
    public record WithMedia(String markdown, Map<String, byte[]> media) {}

    /**
     * The Pandoc input format of an uploaded document, decided by what the file is rather than by what
     * it is called wherever its bytes can tell.
     *
     * <p>A ZIP archive is an OpenDocument text when its {@code mimetype} entry says so, an EPUB likewise,
     * and a Word document when it holds {@code word/document.xml}. Anything else is decided by its name
     * and then by its declared type: RTF, HTML and LaTeX. The old binary Word format ({@code .doc}) is
     * never answered, whatever the file is called, because Pandoc cannot read it; nor is a ZIP archive
     * of any other kind.
     *
     * @param data     the bytes of the file
     * @param fileName the name it arrived under, or null
     * @param mimeType the type it was declared as, or null
     * @return the format, such as {@code docx} or {@code odt}, or empty where Pandoc cannot read it
     */
    public static Optional<String> formatOf(byte[] data, @Nullable String fileName, @Nullable String mimeType) {
        if (isContainer(data)) return containerFormat(data);
        String name = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
        if (name.endsWith(".rtf") || startsWith(data, "{\\rtf")) return Optional.of("rtf");
        if (name.endsWith(".html") || name.endsWith(".htm")) return Optional.of("html");
        if (name.endsWith(".tex") || name.endsWith(".latex")) return Optional.of("latex");
        String type = mimeType == null ? "" : mimeType.toLowerCase(Locale.ROOT);
        if (type.startsWith("text/html")) return Optional.of("html");
        if (type.startsWith("text/rtf") || type.startsWith("application/rtf")) return Optional.of("rtf");
        return Optional.empty();
    }

    /**
     * Whether the bytes are an archive or an old office file rather than text: a ZIP archive, or the
     * compound file the old binary office formats are kept in.
     *
     * @param data the bytes of the file
     * @return whether they are a container of either kind
     */
    public static boolean isContainer(byte[] data) {
        return startsWith(data, ZIP) || startsWith(data, COMPOUND_FILE);
    }

    private static Optional<String> containerFormat(byte[] data) {
        if (!startsWith(data, ZIP)) return Optional.empty();
        boolean word = false;
        try (var zip = new ZipInputStream(new ByteArrayInputStream(data))) {
            for (var entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                if ("mimetype".equals(entry.getName())) {
                    String declared = new String(zip.readNBytes(MAX_MIMETYPE), StandardCharsets.US_ASCII).strip();
                    if (ODT.equals(declared)) return Optional.of("odt");
                    if (EPUB.equals(declared)) return Optional.of("epub");
                }
                if ("word/document.xml".equals(entry.getName())) word = true;
            }
        } catch (IOException notAnArchive) {
            return Optional.empty();
        }
        return word ? Optional.of("docx") : Optional.empty();
    }

    private static boolean startsWith(byte[] data, String prefix) {
        return startsWith(data, prefix.getBytes(StandardCharsets.US_ASCII));
    }

    private static boolean startsWith(byte[] data, byte[] prefix) {
        if (data.length < prefix.length) return false;
        for (int index = 0; index < prefix.length; index++) {
            if (data[index] != prefix[index]) return false;
        }
        return true;
    }

    /**
     * Converts a document to GitHub-Flavored Markdown and keeps the pictures of its body.
     *
     * <p>Pandoc reads the body only: headers, footers and anything drawn in them are left out. The
     * pictures come back by the path the markdown names them with, so the caller can store them and
     * point the markdown at where they went.
     *
     * @param data       the bytes of the document
     * @param fromFormat the Pandoc input format, as {@link #formatOf} answers it
     * @return the markdown and its pictures
     * @throws IOException if conversion fails
     */
    public static WithMedia toMarkdownWithMedia(byte[] data, String fromFormat) throws IOException {
        Path tempDir = Files.createTempDirectory("pandoc-media-");
        try {
            String input = "input." + fromFormat;
            Path outputFile = tempDir.resolve("output.md");
            Files.write(tempDir.resolve(input), data);
            var process = new ProcessBuilder(
                            PANDOC_BIN,
                            "-f",
                            fromFormat,
                            "-t",
                            "gfm",
                            "--wrap=none",
                            "--extract-media=extracted",
                            input,
                            "-o",
                            "output.md")
                    .directory(tempDir.toFile())
                    .redirectErrorStream(true)
                    .start();
            String processOutput = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (process.waitFor() != 0) {
                log.error("Pandoc failed on a {} document: {}", fromFormat, processOutput);
                throw new IOException("Pandoc conversion failed: " + processOutput);
            }
            var media = new LinkedHashMap<String, byte[]>();
            Path extracted = tempDir.resolve("extracted");
            if (Files.isDirectory(extracted)) {
                try (var files = Files.walk(extracted)) {
                    for (Path file : files.filter(Files::isRegularFile).toList()) {
                        media.put(tempDir.relativize(file).toString().replace('\\', '/'), Files.readAllBytes(file));
                    }
                }
            }
            return new WithMedia(Files.readString(outputFile), media);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Pandoc conversion interrupted", e);
        } finally {
            deleteTree(tempDir);
        }
    }

    private static void deleteTree(Path dir) throws IOException {
        try (var walk = Files.walk(dir)) {
            for (Path path : walk.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
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

            String processOutput = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
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
