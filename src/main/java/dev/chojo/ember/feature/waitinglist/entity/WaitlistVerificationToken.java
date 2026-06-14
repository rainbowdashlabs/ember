/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.waitinglist.entity;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.chojo.sadu.mapper.rowmapper.RowMapping;
import org.slf4j.Logger;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;
import static org.slf4j.LoggerFactory.getLogger;

public record WaitlistVerificationToken(
        int id,
        String token,
        int listId,
        String firstname,
        String lastname,
        String email,
        List<GuardianInput> guardians,
        Map<Integer, String> fieldValues,
        String notes,
        Instant createdAt,
        Instant expiresAt) {

    private static final Logger log = getLogger(WaitlistVerificationToken.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<List<GuardianInput>> GUARDIAN_LIST = new TypeReference<>() {};
    private static final TypeReference<Map<Integer, String>> VALUE_MAP = new TypeReference<>() {};

    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }

    public static RowMapping<WaitlistVerificationToken> map() {
        return row -> new WaitlistVerificationToken(
                row.getInt("id"),
                row.getString("token"),
                row.getInt("list_id"),
                row.getString("firstname"),
                row.getString("lastname"),
                row.getString("email"),
                parseGuardians(row.getString("guardians")),
                parseFieldValues(row.getString("field_values")),
                row.getString("notes"),
                row.get("created_at", INSTANT_TIMESTAMP),
                row.get("expires_at", INSTANT_TIMESTAMP));
    }

    private static List<GuardianInput> parseGuardians(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return MAPPER.readValue(json, GUARDIAN_LIST);
        } catch (Exception e) {
            log.warn("Failed to parse guardians JSON: {}", json, e);
            return List.of();
        }
    }

    private static Map<Integer, String> parseFieldValues(String json) {
        if (json == null || json.isBlank()) return Map.of();
        try {
            return MAPPER.readValue(json, VALUE_MAP);
        } catch (Exception e) {
            log.warn("Failed to parse field_values JSON: {}", json, e);
            return Map.of();
        }
    }
}
