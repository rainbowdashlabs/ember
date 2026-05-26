/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

public record EventTemplate(
        int id,
        int stationId,
        String name,
        String title,
        String description,
        Integer categoryId,
        String eventType,
        Boolean requiresRegistration,
        String registrationDeadlineOffset,
        Boolean requiresConfirmation,
        String restrictionMode,
        Integer attendanceTemplateId,
        Integer registrationLimit) {

    public static RowMapping<EventTemplate> map() {
        return row -> new EventTemplate(
                row.getInt("id"),
                row.getInt("station_id"),
                row.getString("name"),
                row.getString("title"),
                row.getString("description"),
                row.getObject("category_id", Integer.class),
                row.getString("event_type"),
                row.getObject("requires_registration", Boolean.class),
                row.getString("registration_deadline_offset"),
                row.getObject("requires_confirmation", Boolean.class),
                row.getString("restriction_mode"),
                row.getObject("attendance_template_id", Integer.class),
                row.getObject("registration_limit", Integer.class));
    }
}
