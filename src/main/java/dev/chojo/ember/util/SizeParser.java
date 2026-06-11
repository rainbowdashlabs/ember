/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses human-readable size strings (e.g. "5G", "50M", "1024K") into bytes.
 * Supported suffixes: K (KiB), M (MiB), G (GiB), T (TiB).
 * Plain numbers are treated as bytes.
 */
public final class SizeParser {
    private static final Pattern SIZE_PATTERN =
            Pattern.compile("^(\\d+(?:\\.\\d+)?)\\s*([KMGT])?$", Pattern.CASE_INSENSITIVE);

    private SizeParser() {}

    /**
     * Parses a size string into bytes.
     *
     * @param value the size string (e.g. "5G", "50M", "1024")
     * @return the size in bytes
     * @throws IllegalArgumentException if the string cannot be parsed
     */
    public static long parseBytes(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Size string must not be blank");
        }
        Matcher matcher = SIZE_PATTERN.matcher(value.trim());
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Invalid size string: " + value);
        }
        double number = Double.parseDouble(matcher.group(1));
        String suffix = matcher.group(2);
        if (suffix == null) {
            return (long) number;
        }
        long multiplier =
                switch (suffix.toUpperCase()) {
                    case "K" -> 1024L;
                    case "M" -> 1024L * 1024;
                    case "G" -> 1024L * 1024 * 1024;
                    case "T" -> 1024L * 1024 * 1024 * 1024;
                    default -> 1L;
                };
        return (long) (number * multiplier);
    }

    /**
     * Formats a byte count into a human-readable string.
     *
     * @param bytes the byte count
     * @return human-readable size (e.g. "1.5 GiB", "50 MiB")
     */
    public static String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024L * 1024) return String.format("%.1f KiB", bytes / 1024.0);
        if (bytes < 1024L * 1024 * 1024) return String.format("%.1f MiB", bytes / (1024.0 * 1024));
        if (bytes < 1024L * 1024 * 1024 * 1024) return String.format("%.1f GiB", bytes / (1024.0 * 1024 * 1024));
        return String.format("%.1f TiB", bytes / (1024.0 * 1024 * 1024 * 1024));
    }
}
