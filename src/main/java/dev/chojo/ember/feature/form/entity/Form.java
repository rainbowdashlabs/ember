/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.form.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;
import de.chojo.sadu.queries.converter.StandardValueConverter;
import dev.chojo.ember.feature.restriction.RestrictionMode;

import java.time.Instant;
import java.util.UUID;

import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

public record Form(
        int id,
        int stationId,
        String title,
        String description,
        FormStatus status,
        boolean shuffleQuestions,
        boolean allowEdit,
        boolean forced,
        Instant startAt,
        Instant endAt,
        Instant closedAt,
        int createdBy,
        Instant createdAt,
        Instant updatedAt,
        RestrictionMode restrictionMode,
        boolean restricted,
        FormPurpose purpose,
        UUID publicUid,
        int responseCount) {

    public static RowMapping<Form> map() {
        return row -> new Form(
                row.getInt("id"),
                row.getInt("station_id"),
                row.getString("title"),
                row.getString("description"),
                row.getEnum("status", FormStatus.class),
                row.getBoolean("shuffle_questions"),
                row.getBoolean("allow_edit"),
                row.getBoolean("forced"),
                row.get("start_at", INSTANT_TIMESTAMP),
                row.get("end_at", INSTANT_TIMESTAMP),
                row.get("closed_at", INSTANT_TIMESTAMP),
                row.getInt("created_by"),
                row.get("created_at", INSTANT_TIMESTAMP),
                row.get("updated_at", INSTANT_TIMESTAMP),
                row.getEnum("restriction_mode", RestrictionMode.class),
                row.getBoolean("restricted"),
                row.getEnum("purpose", FormPurpose.class),
                row.get("public_uid", StandardValueConverter.UUID_STRING),
                row.getInt("response_count"));
    }

    public boolean isAcceptingResponses() {
        if (status != FormStatus.OPEN) return false;
        var now = Instant.now();
        if (startAt != null && now.isBefore(startAt)) return false;
        return endAt == null || !now.isAfter(endAt);
    }

    public enum FormStatus {
        DRAFT,
        OPEN,
        CLOSED
    }
}
