/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

/**
 * One member's answer to one registration question.
 *
 * @param registrationId the registration the answer belongs to
 * @param fieldId        the question being answered
 * @param value          the answer, in the textual shape the event's own field values use
 */
public record RegistrationFieldValue(int registrationId, int fieldId, String value) {

    public static RowMapping<RegistrationFieldValue> map() {
        return row -> new RegistrationFieldValue(
                row.getInt("registration_id"), row.getInt("field_id"), row.getString("value"));
    }
}
