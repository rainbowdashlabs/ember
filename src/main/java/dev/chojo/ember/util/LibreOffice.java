/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.util;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

/**
 * Turns office documents into PDFs with LibreOffice in headless mode. The binary is the one
 * {@code LIBREOFFICE_BIN} names, {@code libreoffice} where it names none.
 *
 * <p>Each conversion runs in a directory of its own that is removed afterwards, with a LibreOffice profile
 * of its own inside it. LibreOffice cannot run two headless instances on one profile and fails without a
 * word when another process, such as a second server or test run on the machine, holds the shared one.
 * Conversions of one process still run one after the other.
 *
 * <p>Fonts a document names that the machine does not have can be handed in as directories: LibreOffice
 * finds them through a fontconfig file that adds the directories to the machine's own configuration, so
 * the document is drawn in them instead of a substitute.
 */
public final class LibreOffice {
    private static final Logger log = LoggerFactory.getLogger(LibreOffice.class);
    private static final String BIN = System.getenv().getOrDefault("LIBREOFFICE_BIN", "libreoffice");
    private static final ReentrantLock LOCK = new ReentrantLock();

    /** The longest a conversion may take before it is given up. */
    private static final long TIMEOUT_SECONDS = 120;

    private LibreOffice() {}

    /**
     * Converts a document to PDF.
     *
     * @param data      the bytes of the document
     * @param extension the extension that tells LibreOffice what the document is, such as {@code docx}
     * @param filter    the PDF export filter with its options, such as
     *                  {@code pdf:writer_pdf_Export:{...}}, or null for the plain export
     * @param fonts     directories of fonts to draw with besides the machine's own, none for none
     * @return the PDF
     * @throws IOException where LibreOffice fails, takes too long or writes no PDF
     */
    public static byte[] toPdf(byte[] data, String extension, @Nullable String filter, List<Path> fonts)
            throws IOException {
        LOCK.lock();
        Path dir = Files.createTempDirectory("libreoffice-convert-");
        try {
            Path input = dir.resolve("input." + extension);
            Files.write(input, data);
            Path transcript = dir.resolve("conversion.log");
            var process = new ProcessBuilder(
                    BIN,
                    "-env:UserInstallation=" + dir.resolve("profile").toUri(),
                    "--headless",
                    "--convert-to",
                    filter == null ? "pdf" : filter,
                    "--outdir",
                    dir.toString(),
                    input.toString());
            process.redirectErrorStream(true).redirectOutput(transcript.toFile());
            if (!fonts.isEmpty()) {
                process.environment()
                        .put("FONTCONFIG_FILE", fontConfig(dir, fonts).toString());
            }
            var running = process.start();
            if (!running.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                running.destroyForcibly();
                throw new IOException("LibreOffice took longer than " + TIMEOUT_SECONDS + " seconds");
            }
            String output = Files.readString(transcript, StandardCharsets.UTF_8);
            if (running.exitValue() != 0) {
                log.error("LibreOffice conversion failed (exit {}): {}", running.exitValue(), output);
                throw new IOException("LibreOffice conversion failed: " + output);
            }
            Path pdf = dir.resolve("input.pdf");
            if (!Files.exists(pdf)) throw new IOException("LibreOffice did not produce a PDF: " + output);
            return Files.readAllBytes(pdf);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("LibreOffice conversion interrupted", e);
        } finally {
            remove(dir);
            LOCK.unlock();
        }
    }

    /**
     * A fontconfig file that reads the machine's configuration, the one named in the environment or the
     * system's, and adds the directories of fonts to it.
     */
    private static Path fontConfig(Path dir, List<Path> fonts) throws IOException {
        String machine = System.getenv().getOrDefault("FONTCONFIG_FILE", "/etc/fonts/fonts.conf");
        String directories = fonts.stream()
                .map(font -> "  <dir>" + escaped(font.toString()) + "</dir>")
                .collect(Collectors.joining("\n"));
        Path file = dir.resolve("fonts.conf");
        Files.writeString(file, """
                <?xml version="1.0"?>
                <!DOCTYPE fontconfig SYSTEM "fonts.dtd">
                <fontconfig>
                  <include ignore_missing="yes">%s</include>
                %s
                  <cachedir>%s</cachedir>
                </fontconfig>
                """.formatted(
                escaped(machine), directories, escaped(dir.resolve("fc-cache").toString())));
        return file;
    }

    private static String escaped(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static void remove(Path dir) {
        try (var walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException e) {
                    log.debug("A file of a LibreOffice conversion was left behind: {}", path, e);
                }
            });
        } catch (IOException e) {
            log.debug("The directory of a LibreOffice conversion was left behind: {}", dir, e);
        }
    }
}
