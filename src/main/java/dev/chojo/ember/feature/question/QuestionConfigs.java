/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.question;

import dev.chojo.ember.util.Json;
import org.slf4j.Logger;
import tools.jackson.databind.ObjectMapper;

import static org.slf4j.LoggerFactory.getLogger;

/**
 * Reading and writing the settings a feature stores beside a question.
 *
 * <p>Six features kept the same fifteen lines: a mapper, a logger, a read that falls back to empty
 * settings where the column says something unreadable, and a write that falls back to an empty
 * object. Two of the six used a mapper that refuses to write a config with nothing in it and three
 * used one that does not, which is the kind of difference nobody chose and nobody could see.
 *
 * <p>A stale or unreadable column is a field to fix rather than a feature to take out, which is why
 * a bad read is logged and answered with the empty settings rather than thrown.
 */
public final class QuestionConfigs {
    private static final Logger log = getLogger(QuestionConfigs.class);
    private static final ObjectMapper MAPPER = Json.EMPTY_TOLERANT_CONFIG_MAPPER;

    private QuestionConfigs() {}

    /**
     * The settings a column holds, or the empty ones where it holds nothing or nothing readable.
     *
     * @param json  the column as it stands
     * @param type  the settings record this feature keeps
     * @param empty what a field with no settings at all reads as
     */
    public static <T> T parse(String json, Class<T> type, T empty) {
        if (json == null || json.isBlank() || "{}".equals(json.trim())) return empty;
        try {
            return MAPPER.readValue(json, type);
        } catch (Exception e) {
            log.error("Failed to read the settings of a {}: {}", type.getSimpleName(), json, e);
            return empty;
        }
    }

    /**
     * The settings as they are stored, or an empty object where they cannot be written at all.
     *
     * @param config the settings of one question
     */
    public static String toJson(Object config) {
        try {
            return MAPPER.writeValueAsString(config);
        } catch (Exception e) {
            log.error("Failed to write the settings of a {}", config.getClass().getSimpleName(), e);
            return "{}";
        }
    }
}
