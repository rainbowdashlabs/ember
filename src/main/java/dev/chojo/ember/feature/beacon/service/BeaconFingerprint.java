/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.beacon.service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * The name a fault goes by on a beacon, across every instance and every release that hit it.
 *
 * <p>Not the key the local error log groups by. That one is the logger, the exception class and the
 * first frame as Logback prints it, and Logback prints a line number. A line moves on almost every
 * release, so the same fault would arrive as a new fault after every deployment and the beacon could
 * never answer the question it exists to answer: how many installations hit this, and which versions
 * did. What is left after the line numbers go is the shape of the fault, which is what actually
 * stays the same.
 *
 * <p>Lambda and synthetic frames go too. Their names carry a counter the compiler hands out, so two
 * builds of the same source can disagree about them.
 */
public final class BeaconFingerprint {

    private static final Pattern FRAME = Pattern.compile("^(?<cls>[^(]+?)\\.(?<method>[^.(]+)\\(.*$");
    private static final Pattern SYNTHETIC = Pattern.compile("\\$\\$Lambda|lambda\\$|\\$\\d+");
    private static final int FRAMES_KEPT = 12;

    private BeaconFingerprint() {}

    /**
     * Builds the fingerprint from what the local log holds.
     *
     * @param exceptionClass the exception the fault threw, or null where it had none
     * @param stacktrace     the printed stacktrace, one frame per line
     * @param logger         the logger that reported it, which stands in when there is no exception
     * @return a stable name for this fault
     */
    public static String of(String exceptionClass, String stacktrace, String logger) {
        var frames = frameNames(stacktrace);
        if (frames.isEmpty()) {
            return (exceptionClass != null && !exceptionClass.isBlank() ? exceptionClass : logger) + "|";
        }
        return (exceptionClass != null && !exceptionClass.isBlank() ? exceptionClass : logger)
                + "|"
                + String.join(">", frames);
    }

    /**
     * The class and method of each frame, without the file or the line, and without the frames a
     * compiler invented.
     *
     * @param stacktrace the printed stacktrace
     * @return the frames worth naming, at most {@value #FRAMES_KEPT} of them
     */
    public static List<String> frameNames(String stacktrace) {
        var names = new ArrayList<String>();
        if (stacktrace == null || stacktrace.isBlank()) return names;
        for (String raw : stacktrace.split("\n")) {
            String line = raw.strip();
            if (!line.startsWith("at ")) continue;
            var matcher = FRAME.matcher(line.substring(3).strip());
            if (!matcher.matches()) continue;
            String name = matcher.group("cls") + "." + matcher.group("method");
            if (SYNTHETIC.matcher(name).find()) continue;
            names.add(name);
            if (names.size() == FRAMES_KEPT) break;
        }
        return names;
    }
}
