/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;

/**
 * Writes error reports to a directory during dev mode.
 * The filename is the SHA-256 hash of the stacktrace (without message),
 * so duplicate errors don't create multiple files.
 */
public final class DevErrorWriter {
    private static final Logger log = LoggerFactory.getLogger(DevErrorWriter.class);
    private static final Path ERROR_DIR = Path.of("dev-errors");

    private DevErrorWriter() {}

    /**
     * Writes an error report for a Java exception.
     *
     * @param throwable the exception
     * @param context   additional context (e.g., "GET /api/v1/events")
     */
    public static void write(Throwable throwable, String context) {
        try {
            ensureDir();
            String traceKey = buildTraceKey(throwable);
            String hash = sha256(traceKey);
            Path file = ERROR_DIR.resolve(hash + ".txt");

            if (Files.exists(file)) {
                // Update last-seen timestamp at the end
                String existing = Files.readString(file);
                int lastSeenIdx = existing.lastIndexOf("Last seen: ");
                if (lastSeenIdx >= 0) {
                    String updated = existing.substring(0, lastSeenIdx) + "Last seen: " + Instant.now();
                    Files.writeString(file, updated);
                }
                return;
            }

            var sw = new StringWriter();
            sw.write("Source: backend\n");
            sw.write("Context: " + context + "\n");
            sw.write("Exception: " + throwable.getClass().getName() + "\n");
            sw.write("Message: " + throwable.getMessage() + "\n");
            sw.write("First seen: " + Instant.now() + "\n");
            sw.write("Last seen: " + Instant.now() + "\n");
            sw.write("\n--- Stacktrace ---\n");
            throwable.printStackTrace(new PrintWriter(sw));
            Files.writeString(file, sw.toString(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.warn("Failed to write dev error file", e);
        }
    }

    /**
     * Writes an error report from a frontend error.
     *
     * @param source  the source identifier (e.g., "vue", "axios", "window")
     * @param message the error message
     * @param stack   the stack trace string
     * @param context additional context (e.g., URL, component name)
     */
    public static void writeFrontend(String source, String message, String stack, String context) {
        try {
            ensureDir();
            String traceKey = stripMessages(stack);
            String hash = sha256(traceKey);
            Path file = ERROR_DIR.resolve(hash + ".txt");

            if (Files.exists(file)) {
                String existing = Files.readString(file);
                int lastSeenIdx = existing.lastIndexOf("Last seen: ");
                if (lastSeenIdx >= 0) {
                    String updated = existing.substring(0, lastSeenIdx) + "Last seen: " + Instant.now();
                    Files.writeString(file, updated);
                }
                return;
            }

            var content = "Source: frontend (" + source + ")\n"
                    + "Context: " + context + "\n"
                    + "Message: " + message + "\n"
                    + "First seen: " + Instant.now() + "\n"
                    + "Last seen: " + Instant.now() + "\n"
                    + "\n--- Stacktrace ---\n"
                    + stack + "\n";
            Files.writeString(file, content, StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.warn("Failed to write frontend dev error file", e);
        }
    }

    /**
     * Builds a hash key from the stacktrace without messages.
     * Uses class name + method + file + line number for each frame.
     */
    private static String buildTraceKey(Throwable throwable) {
        var sb = new StringBuilder();
        sb.append(throwable.getClass().getName()).append('\n');
        for (var el : throwable.getStackTrace()) {
            sb.append(el.getClassName())
                    .append('.')
                    .append(el.getMethodName())
                    .append(':')
                    .append(el.getLineNumber())
                    .append('\n');
        }
        if (throwable.getCause() != null) {
            sb.append("caused by: ").append(buildTraceKey(throwable.getCause()));
        }
        return sb.toString();
    }

    /**
     * Strips messages from a JS stacktrace, keeping only file:line:col references.
     */
    private static String stripMessages(String stack) {
        if (stack == null || stack.isBlank()) return "";
        var lines = stack.split("\n");
        var sb = new StringBuilder();
        for (var line : lines) {
            var trimmed = line.trim();
            if (trimmed.startsWith("at ") || trimmed.contains("@")) {
                sb.append(trimmed).append('\n');
            }
        }
        return sb.toString();
    }

    private static String sha256(String input) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            var hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash).substring(0, 16);
        } catch (NoSuchAlgorithmException e) {
            return String.valueOf(input.hashCode());
        }
    }

    private static void ensureDir() throws IOException {
        if (!Files.exists(ERROR_DIR)) {
            Files.createDirectories(ERROR_DIR);
        }
    }
}
