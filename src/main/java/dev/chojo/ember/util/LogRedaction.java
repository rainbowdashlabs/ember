/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.util;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Redaction helpers for trace-level request / response logging. Keys and header
 * names that are known to carry credentials are matched case-insensitively and
 * their values are replaced with the sentinel {@code [REDACTED]}. The original
 * inputs are not modified — every method returns a fresh string or map.
 */
public final class LogRedaction {

    public static final String SENTINEL = "[REDACTED]";

    /**
     * Query-string keys whose values may carry a credential. The legacy
     * {@code ?token=}/{@code ?stationId=} fallbacks are gone but historical
     * URLs may still appear in TRACE logs of a long-running process; the
     * denylist is conservative for that reason.
     */
    public static final Set<String> REDACTED_QUERY_KEYS = Set.of("token", "stationid");

    /**
     * Header names whose values are credentials or carry session-binding data.
     */
    public static final Set<String> REDACTED_HEADERS =
            Set.of("authorization", "cookie", "set-cookie", "x-federation-signature", "x-station-id");

    private LogRedaction() {}

    /**
     * Returns a redacted copy of {@code rawQueryString}. Each {@code key=value}
     * pair whose key (case-insensitive) is in {@link #REDACTED_QUERY_KEYS} has
     * its value replaced; everything else is preserved verbatim.
     */
    public static String redactQueryString(String rawQueryString) {
        if (rawQueryString == null || rawQueryString.isEmpty()) return "";
        String[] pairs = rawQueryString.split("&");
        var out = new StringBuilder(rawQueryString.length());
        for (int i = 0; i < pairs.length; i++) {
            if (i > 0) out.append('&');
            String pair = pairs[i];
            int eq = pair.indexOf('=');
            if (eq < 0) {
                out.append(pair);
                continue;
            }
            String key = pair.substring(0, eq);
            String lower = key.toLowerCase(Locale.ROOT);
            if (REDACTED_QUERY_KEYS.contains(lower)) {
                out.append(key).append('=').append(SENTINEL);
            } else {
                out.append(pair);
            }
        }
        return out.toString();
    }

    /**
     * Returns a redacted copy of {@code headers}. Values for header names in
     * {@link #REDACTED_HEADERS} (case-insensitive) are replaced with
     * {@link #SENTINEL}; other entries are preserved verbatim. The input map
     * is not modified.
     */
    public static Map<String, String> redactHeaders(Map<String, String> headers) {
        if (headers == null || headers.isEmpty()) return Map.of();
        var out = new LinkedHashMap<String, String>(headers.size());
        for (var e : headers.entrySet()) {
            String key = e.getKey();
            String lower = key == null ? "" : key.toLowerCase(Locale.ROOT);
            out.put(key, REDACTED_HEADERS.contains(lower) ? SENTINEL : e.getValue());
        }
        return out;
    }
}
