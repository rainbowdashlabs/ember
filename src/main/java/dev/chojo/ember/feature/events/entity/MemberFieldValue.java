/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.entity;

import java.util.ArrayList;
import java.util.List;

/**
 * Helpers for the textual representation of MEMBER-type field values. Single-value
 * fields store a bare integer ({@code "42"}); list fields store a JSON array
 * ({@code "[42,17]"}). Both shapes are tolerated when reading.
 */
public final class MemberFieldValue {
    private MemberFieldValue() {}

    public static List<Integer> parseIds(String value) {
        var ids = new ArrayList<Integer>();
        if (value == null || value.isBlank()) return ids;
        String cleaned = value.trim();
        try {
            if (cleaned.startsWith("[")) {
                cleaned = cleaned.replaceAll("[\\[\\]\"\\s]", "");
                for (String part : cleaned.split(",")) {
                    if (!part.isBlank()) ids.add(Integer.parseInt(part.trim()));
                }
            } else {
                cleaned = cleaned.replace("\"", "").trim();
                if (!cleaned.isBlank()) ids.add(Integer.parseInt(cleaned));
            }
        } catch (NumberFormatException ignored) {
        }
        return ids;
    }

    public static String formatList(List<Integer> ids) {
        var sb = new StringBuilder("[");
        for (int i = 0; i < ids.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(ids.get(i));
        }
        sb.append("]");
        return sb.toString();
    }

    public static String formatSingle(Integer id) {
        return id == null ? "" : String.valueOf(id);
    }
}
