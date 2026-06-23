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
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;

public final class DevErrorWriter {
    private static final Logger log = LoggerFactory.getLogger(DevErrorWriter.class);
    private static final Path ERROR_DIR = Path.of("dev-errors");
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH-mm-ss");

    private DevErrorWriter() {}

    public static void write(Throwable throwable, String context) {
        try {
            ensureDir();
            String traceKey = buildTraceKey(throwable);
            String hash = sha256(traceKey);
            String time = LocalTime.now(ZoneId.systemDefault()).format(TIME_FMT);
            Path file = ERROR_DIR.resolve(time + " - backend - " + hash + ".txt");

            if (hashFileExists(hash)) return;

            var sw = new StringWriter();
            sw.write("Source: backend\n");
            sw.write("Context: " + context + "\n");
            sw.write("Exception: " + throwable.getClass().getName() + "\n");
            sw.write("Message: " + throwable.getMessage() + "\n");
            sw.write("Time: " + Instant.now() + "\n");
            sw.write("\n--- Stacktrace ---\n");
            throwable.printStackTrace(new PrintWriter(sw));
            Files.writeString(file, sw.toString(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.warn("Failed to write dev error file", e);
        }
    }

    public static void writeFrontend(String source, String message, String stack, String context) {
        try {
            ensureDir();
            String traceKey = stripMessages(stack);
            String hash = sha256(traceKey);
            String time = LocalTime.now(ZoneId.systemDefault()).format(TIME_FMT);
            Path file = ERROR_DIR.resolve(time + " - frontend - " + hash + ".txt");

            if (hashFileExists(hash)) return;

            var content = "Source: frontend (" + source + ")\n"
                    + "Context: " + context + "\n"
                    + "Message: " + message + "\n"
                    + "Time: " + Instant.now() + "\n"
                    + "\n--- Stacktrace ---\n"
                    + stack + "\n";
            Files.writeString(file, content, StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.warn("Failed to write frontend dev error file", e);
        }
    }

    public static void clearOnStartup() {
        try {
            if (Files.exists(ERROR_DIR)) {
                try (var files = Files.list(ERROR_DIR)) {
                    files.filter(p -> p.toString().endsWith(".txt")).forEach(p -> {
                        try {
                            Files.delete(p);
                        } catch (IOException ignored) {
                        }
                    });
                }
            }
        } catch (IOException e) {
            log.warn("Failed to clear dev-errors directory", e);
        }
    }

    private static boolean hashFileExists(String hash) throws IOException {
        try (var files = Files.list(ERROR_DIR)) {
            return files.anyMatch(p -> p.getFileName().toString().contains(hash));
        }
    }

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
