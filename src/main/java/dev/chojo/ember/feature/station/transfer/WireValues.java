/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.station.transfer;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Coercions for values read off the transfer wire, where a column can arrive as the driver's
 * native type, as a JSON scalar, or as a string depending on the source instance's version.
 */
public final class WireValues {

    private WireValues() {}

    public static Integer asInteger(Object val) {
        if (val instanceof Number n) return n.intValue();
        if (val instanceof String s) {
            try {
                return Integer.parseInt(s);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    public static String asString(Object o, String defaultValue) {
        return o == null ? defaultValue : o.toString();
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> asMap(Object o) {
        return o instanceof Map ? (Map<String, Object>) o : null;
    }

    /**
     * Parses a UUID, returning {@code null} for absent or malformed values instead of throwing.
     */
    public static UUID asUuid(Object raw) {
        if (raw == null) return null;
        try {
            return UUID.fromString(raw.toString());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Coerces a wire-format timestamp value to {@link Instant}. The canonical wire format is
     * epoch milliseconds (long) emitted by {@code GenericTableExporter}, but legacy / mixed
     * payloads with ISO-8601 strings or driver-native types are accepted so the importer keeps
     * working through a wire-format transition.
     */
    public static Instant asInstant(Object val) {
        return switch (val) {
            case null -> null;
            case Instant i -> i;
            case Number n -> Instant.ofEpochMilli(n.longValue());
            case String s when !s.isBlank() -> Instant.parse(s);
            default -> null;
        };
    }
}
