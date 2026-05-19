/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.form.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

import java.time.Instant;

import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

public record FormResponse(int id, int formId, int memberId, int submittedBy, Instant submittedAt, Instant updatedAt) {
    public static RowMapping<FormResponse> map() {
        return row -> new FormResponse(
                row.getInt("id"),
                row.getInt("form_id"),
                row.getInt("member_id"),
                row.getInt("submitted_by"),
                row.get("submitted_at", INSTANT_TIMESTAMP),
                row.get("updated_at", INSTANT_TIMESTAMP));
    }
}
