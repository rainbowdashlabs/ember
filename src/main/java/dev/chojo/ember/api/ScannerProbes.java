/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.api;

import java.util.Set;

/**
 * Recognises the addresses a vulnerability scanner tries on every host it can reach.
 *
 * <p>Any instance on the open internet is asked for {@code /.env}, {@code /.git/config} and a few
 * hundred relatives within days of being reachable. None of them is a route this application has, so
 * each answered 404 and was written down as a fault, and because the address is part of what is written
 * down, every spelling of the probe became an entry of its own. That buried the 404s worth reading, the
 * ones where something that should exist is being asked for and is not there.
 *
 * <p>What is recognised here is quietened rather than refused: the answer is the same 404 either way,
 * and a probe that a future scanner spells differently is only noise in the log, never a wrong answer.
 */
public final class ScannerProbes {
    /**
     * Names beginning with a dot are configuration and credentials, never a route. The one exception is
     * the directory the web asks for by agreement, which is a real address and not a probe.
     */
    private static final String AGREED_DOT_DIRECTORY = ".well-known";

    /** What a probe asks for by file type, none of which this application ever serves. */
    private static final Set<String> PROBE_SUFFIXES =
            Set.of(".php", ".asp", ".aspx", ".jsp", ".cgi", ".sql", ".bak", ".old", ".ini", ".yml", ".yaml");

    /** Directories belonging to software this application is not, asked for by name. */
    private static final Set<String> PROBE_SEGMENTS = Set.of(
            "wp-admin",
            "wp-content",
            "wp-includes",
            "wordpress",
            "phpmyadmin",
            "phpinfo",
            "cgi-bin",
            "actuator",
            "boaform",
            "hudson",
            "jenkins",
            "solr",
            "telescope",
            "_ignition");

    private ScannerProbes() {}

    /**
     * Whether this address is one a scanner tries rather than one a client of this application asks for.
     *
     * @param path the address that was asked for, as the request carried it
     * @return {@code true} where the address is a known probe and its 404 is not worth an operator's
     *         attention
     */
    public static boolean looksLikeAProbe(String path) {
        if (path == null || path.isBlank()) return false;
        String lower = path.toLowerCase();
        for (String suffix : PROBE_SUFFIXES) {
            if (lower.endsWith(suffix)) return true;
        }
        for (String segment : lower.split("/")) {
            if (segment.isEmpty()) continue;
            if (PROBE_SEGMENTS.contains(segment)) return true;
            if (segment.startsWith(".") && !segment.equals(AGREED_DOT_DIRECTORY)) return true;
        }
        return false;
    }
}
