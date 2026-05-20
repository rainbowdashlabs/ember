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

/**
 * Carries the localizable content and optional navigation link for a notification.
 * Serialized as JSONB in the database.
 *
 * @param localeKey i18n message key for the notification text
 * @param params    interpolation parameters for the message template
 * @param link      optional link to a frontend route, or {@code null}
 */
public record NotificationData(String localeKey, Map<String, String> params, NotificationLink link) {

    private static final ObjectMapper MAPPER = JsonMapper.builder()
            .changeDefaultVisibility(v -> v.withFieldVisibility(JsonAutoDetect.Visibility.ANY)
                    .withGetterVisibility(JsonAutoDetect.Visibility.NONE))
            .build();

    /**
     * Creates notification data with a navigation link.
     *
     * @param localeKey i18n message key
     * @param params    interpolation parameters
     * @param link      navigation link
     * @return new notification data instance
     */
    public static NotificationData of(String localeKey, Map<String, String> params, NotificationLink link) {
        return new NotificationData(localeKey, params, link);
    }

    /**
     * Creates notification data without a navigation link.
     *
     * @param localeKey i18n message key
     * @param params    interpolation parameters
     * @return new notification data instance
     */
    public static NotificationData of(String localeKey, Map<String, String> params) {
        return new NotificationData(localeKey, params, null);
    }

    /**
     * Deserializes notification data from a JSON string.
     *
     * @param json the JSON representation
     * @return the deserialized notification data
     * @throws RuntimeException if deserialization fails
     */
    public static NotificationData fromJson(String json) {
        try {
            return MAPPER.readValue(json, NotificationData.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to deserialize NotificationData", e);
        }
    }

    /**
     * Serializes this notification data to a JSON string.
     *
     * @return the JSON representation
     * @throws RuntimeException if serialization fails
     */
    public String toJson() {
        try {
            return MAPPER.writeValueAsString(this);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize NotificationData", e);
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
    }
}
