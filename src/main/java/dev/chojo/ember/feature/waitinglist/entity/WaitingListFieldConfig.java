/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.waitinglist.entity;

import com.fasterxml.jackson.annotation.JsonInclude;
import dev.chojo.ember.util.Json;
import org.slf4j.Logger;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.slf4j.LoggerFactory.getLogger;

/**
 * Configuration for a waiting list field, stored as JSONB.
 *
 * @param options     selectable values for choice-type fields
 * @param placeholder placeholder text
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record WaitingListFieldConfig(List<String> options, String placeholder) {
    public static final WaitingListFieldConfig EMPTY = new WaitingListFieldConfig(null, null);
    private static final Logger log = getLogger(WaitingListFieldConfig.class);
    private static final ObjectMapper MAPPER = Json.EMPTY_TOLERANT_CONFIG_MAPPER;

    public static WaitingListFieldConfig parse(String json) {
        if (json == null || json.isBlank() || "{}".equals(json)) return EMPTY;
        try {
            return MAPPER.readValue(json, WaitingListFieldConfig.class);
        } catch (Exception e) {
            log.error("Failed to parse waiting list field config: {}", json, e);
            return EMPTY;
        }
    }

    public String toJson() {
        try {
            return MAPPER.writeValueAsString(this);
        } catch (Exception e) {
            return "{}";
        }
    }
}
