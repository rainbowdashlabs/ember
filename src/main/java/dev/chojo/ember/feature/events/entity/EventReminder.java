/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

public record EventReminder(int id, int eventId, int daysBefore) {

    public static RowMapping<EventReminder> map() {
        return row -> new EventReminder(row.getInt("id"), row.getInt("event_id"), row.getInt("days_before"));
    }
}
