/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

public record EventTemplateReminder(int id, int templateId, int daysBefore) {

    public static RowMapping<EventTemplateReminder> map() {
        return row -> new EventTemplateReminder(row.getInt("id"), row.getInt("template_id"), row.getInt("days_before"));
    }
}
