/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;
import dev.chojo.ember.feature.question.FieldType;

/**
 * A registration question an appointment template carries. Creating an appointment from the template
 * copies these into it; the copies are independent afterwards, so editing a template never rewrites
 * questions members have already answered.
 *
 * <p>What the server works with; screens read an {@link EventTemplateRegistrationField}.
 */
public record RegistrationTemplateField(
        int id,
        int templateId,
        String name,
        FieldType fieldType,
        EventQuestionSettings config,
        int position,
        boolean overview) {

    public static RowMapping<RegistrationTemplateField> map() {
        return row -> new RegistrationTemplateField(
                row.getInt("id"),
                row.getInt("template_id"),
                row.getString("name"),
                FieldType.valueOf(row.getString("field_type")),
                EventQuestionSettings.parse(row.getString("config")),
                row.getInt("position"),
                row.getBoolean("overview"));
    }
}
