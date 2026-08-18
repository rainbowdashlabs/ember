/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

/**
 * A registration question carried by an event template. Creating an event from the template copies
 * these into the event; the copies are independent afterwards, so editing a template never rewrites
 * questions members have already answered.
 */
public record EventTemplateRegistrationField(
        int id,
        int templateId,
        String name,
        EventFieldType fieldType,
        EventRegistrationFieldConfig config,
        int position,
        boolean overview) {

    public static RowMapping<EventTemplateRegistrationField> map() {
        return row -> new EventTemplateRegistrationField(
                row.getInt("id"),
                row.getInt("template_id"),
                row.getString("name"),
                row.getEnum("field_type", EventFieldType.class),
                EventRegistrationFieldConfig.parse(row.getString("config")),
                row.getInt("position"),
                row.getBoolean("overview"));
    }
}
