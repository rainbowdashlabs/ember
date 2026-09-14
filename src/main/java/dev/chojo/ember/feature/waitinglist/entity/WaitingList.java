/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.waitinglist.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;
import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.util.Json;
import tools.jackson.core.type.TypeReference;

import java.time.Instant;
import java.util.List;

import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

/**
 * A waiting list of a station.
 *
 * @param sendsMail whether the list writes to the people on it. A list that does not sends neither
 *                  the confirmation link of a public registration nor an invitation, and its
 *                  confirmation cycle with its reminders, its warning and the removal that follows
 *                  them does not run: an installation nobody outside can reach sends links nobody
 *                  can open, and a list kept for local tracking speaks to its people in person.
 */
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
        StationUserType joinUserType,
        int attendanceThreshold,
        boolean isPublic,
        boolean sendsMail,
        Integer minAgeRegister,
        Integer minAgeJoin) {

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
                row.getEnum("join_user_type", StationUserType.class),
                row.getInt("attendance_threshold"),
                row.getBoolean("public"),
                row.getBoolean("sends_mail"),
                row.getObject("min_age_register", Integer.class),
                row.getObject("min_age_join", Integer.class));
    }

    private static List<Integer> parseVisibleFields(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return Json.MAPPER.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            return List.of();
        }
    }
}
