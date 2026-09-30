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
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.function.Supplier;

public final class DevErrorWriter {
    private static final Logger log = LoggerFactory.getLogger(DevErrorWriter.class);
    private static final Path ERROR_DIR = Path.of("dev-errors");
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH-mm-ss");

    private DevErrorWriter() {}

    public static void write(Throwable throwable, String context) {
        store("backend", () -> buildTraceKey(throwable), () -> {
            var sw = new StringWriter();
            sw.write("Source: backend\n");
            sw.write("Context: " + context + "\n");
            sw.write("Exception: " + throwable.getClass().getName() + "\n");
            sw.write("Message: " + throwable.getMessage() + "\n");
            sw.write("Time: " + Instant.now() + "\n");
            sw.write("\n--- Stacktrace ---\n");
            throwable.printStackTrace(new PrintWriter(sw));
            return sw.toString();
        });
    }

    public static void writeFrontend(String source, String message, String stack, String context) {
        store(
                "frontend",
                () -> stripMessages(stack),
                () -> "Source: frontend (" + source + ")\n"
                        + "Context: " + context + "\n"
                        + "Message: " + message + "\n"
                        + "Time: " + Instant.now() + "\n"
                        + "\n--- Stacktrace ---\n"
                        + stack + "\n");
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

    /** Writes one file per distinct trace, named by the time and a hash of the trace. */
    private static void store(String origin, Supplier<String> traceKey, Supplier<String> content) {
        try {
            Files.createDirectories(ERROR_DIR);
            String hash = Sha256.hexPrefix(traceKey.get(), 16);
            if (hashFileExists(hash)) return;
            String time = LocalTime.now(ZoneId.systemDefault()).format(TIME_FMT);
            Path file = ERROR_DIR.resolve(time + " - " + origin + " - " + hash + ".txt");
            Files.writeString(file, content.get(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.warn("Failed to write {} dev error file", origin, e);
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
        var sb = new StringBuilder();
        for (var line : stack.split("\n")) {
            var trimmed = line.trim();
            if (trimmed.startsWith("at ") || trimmed.contains("@")) {
                sb.append(trimmed).append('\n');
            }
        }
        return sb.toString();
    }
}
