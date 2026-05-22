/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.waitinglist.entity;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.chojo.sadu.mapper.rowmapper.RowMapping;

import java.time.Instant;
import java.util.List;

import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

public record WaitingList(
        int id,
        int stationId,
        String name,
        String description,
        String scoringFormula,
        int confirmIntervalDays,
        Instant createdAt,
        List<Integer> visibleFields,
        Integer testingGroupId,
        Integer joinGroupId,
        Integer joinRoleId,
        int attendanceThreshold) {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static RowMapping<WaitingList> map() {
        return row -> new WaitingList(
                row.getInt("id"),
                row.getInt("station_id"),
                row.getString("name"),
                row.getString("description"),
                row.getString("scoring_formula"),
                row.getInt("confirm_interval_days"),
                row.get("created_at", INSTANT_TIMESTAMP),
                parseVisibleFields(row.getString("visible_fields")),
                row.getObject("testing_group_id", Integer.class),
                row.getObject("join_group_id", Integer.class),
                row.getObject("join_role_id", Integer.class),
                row.getInt("attendance_threshold"));
    }

    private static List<Integer> parseVisibleFields(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return MAPPER.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            return List.of();
        }
    }
}
