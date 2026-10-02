/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.form.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;
import dev.chojo.ember.util.Json;
import tools.jackson.core.type.TypeReference;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

/**
 * A form a member started and has not sent yet, kept so it can be continued on any device.
 *
 * <p>A draft is not an answer. It is never counted in the results, never listed as answered and
 * never exported, and a member with only a draft still owes the answer.
 *
 * @param answers   the answers filled in so far, by question id
 * @param path      the keys of the pages visited so far, the page to continue on last
 * @param updatedAt when the draft was last saved
 */
public record FormDraft(Map<Integer, FormAnswerValue> answers, List<String> path, Instant updatedAt) {
    private static final TypeReference<Map<Integer, FormAnswerValue>> ANSWERS = new TypeReference<>() {};
    private static final TypeReference<List<String>> PATH = new TypeReference<>() {};

    /**
     * Creates a row mapping for database result set conversion.
     */
    public static RowMapping<FormDraft> map() {
        return row -> new FormDraft(
                read(row.getString("answers"), ANSWERS, new LinkedHashMap<>()),
                read(row.getString("path"), PATH, List.of()),
                row.get("updated_at", INSTANT_TIMESTAMP));
    }

    /**
     * The answers written for storage.
     *
     * @param answers the answers, by question id
     * @return the stored value
     */
    public static String answersJson(Map<Integer, FormAnswerValue> answers) {
        return Json.MAPPER.writerFor(ANSWERS).writeValueAsString(answers == null ? Map.of() : answers);
    }

    private static <T> T read(String json, TypeReference<T> type, T fallback) {
        if (json == null || json.isBlank()) return fallback;
        try {
            return Json.MAPPER.readValue(json, type);
        } catch (Exception e) {
            return fallback;
        }
    }
}
