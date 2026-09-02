/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.notifications.entity;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Carries the notification parameters and optional navigation link.
 * Serialized as JSONB in the database.
 * <p>
 * The locale key is derived from {@link NotificationType#localeKey()}, not stored here.
 *
 * @param params typed notification parameters
 * @param link   optional link to a frontend route, or {@code null}
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record NotificationData(NotificationParams params, NotificationLink link) {

    private static final ObjectMapper MAPPER = JsonMapper.builder()
            .changeDefaultVisibility(v -> v.withFieldVisibility(JsonAutoDetect.Visibility.ANY)
                    .withGetterVisibility(JsonAutoDetect.Visibility.NONE))
            .build();

    public static NotificationData of(NotificationParams params, NotificationLink link) {
        return new NotificationData(params, link);
    }

    public static NotificationData of(NotificationParams params) {
        return new NotificationData(params, null);
    }

    /**
     * Deserializes notification data from JSON, using the type's params class for correct deserialization.
     */
    public static NotificationData fromJson(String json, NotificationType type) {
        try {
            var tree = MAPPER.readTree(json);
            NotificationParams params = null;
            var paramsNode = tree.get("params");
            if (paramsNode != null && !paramsNode.isNull()) {
                params = MAPPER.treeToValue(paramsNode, type.paramsType());
            }
            NotificationLink link = null;
            var linkNode = tree.get("link");
            if (linkNode != null && !linkNode.isNull()) {
                link = MAPPER.treeToValue(linkNode, NotificationLink.class);
            }
            return new NotificationData(params, link);
        } catch (Exception e) {
            throw new RuntimeException("Failed to deserialize NotificationData", e);
        }
    }

    /**
     * Serializes this notification data to a JSON string.
     */
    public String toJson() {
        try {
            return MAPPER.writeValueAsString(this);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize NotificationData", e);
        }
    }

    /**
     * Converts the typed params to a flat string map for API responses / i18n interpolation.
     */
    @SuppressWarnings("unchecked")
    public Map<String, String> paramsAsMap() {
        if (params == null) return Map.of();
        try {
            var map = (Map<String, Object>) MAPPER.convertValue(params, Map.class);
            var result = new LinkedHashMap<String, String>();
            for (var entry : map.entrySet()) {
                if (entry.getValue() != null) {
                    result.put(entry.getKey(), String.valueOf(entry.getValue()));
                }
            }
            return result;
        } catch (Exception e) {
            return Map.of();
        }
    }

    /**
     * A link to a frontend route, optionally with route parameters.
     *
     * @param route       the named frontend route
     * @param routeParams parameters to interpolate into the route path
     */
    public record NotificationLink(String route, Map<String, Object> routeParams) {
        public NotificationLink(String route) {
            this(route, Map.of());
        }

        /**
         * Serializes the link on its own, for matching stored notifications by what they point at
         * rather than by the words they carry.
         */
        public String toJson() {
            try {
                return MAPPER.writeValueAsString(this);
            } catch (Exception e) {
                throw new RuntimeException("Failed to serialize NotificationLink", e);
            }
        }
    }
}
