/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.notifications.entity;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;

public record NotificationData(String localeKey, Map<String, String> params, NotificationLink link) {

    private static final ObjectMapper MAPPER = JsonMapper.builder()
            .changeDefaultVisibility(v -> v.withFieldVisibility(JsonAutoDetect.Visibility.ANY)
                    .withGetterVisibility(JsonAutoDetect.Visibility.NONE))
            .build();

    public static NotificationData of(String localeKey, Map<String, String> params, NotificationLink link) {
        return new NotificationData(localeKey, params, link);
    }

    public static NotificationData of(String localeKey, Map<String, String> params) {
        return new NotificationData(localeKey, params, null);
    }

    public static NotificationData fromJson(String json) {
        try {
            return MAPPER.readValue(json, NotificationData.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to deserialize NotificationData", e);
        }
    }

    public String toJson() {
        try {
            return MAPPER.writeValueAsString(this);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize NotificationData", e);
        }
    }

    public record NotificationLink(String route, Map<String, Object> routeParams) {
        public NotificationLink(String route) {
            this(route, Map.of());
        }
    }
}
