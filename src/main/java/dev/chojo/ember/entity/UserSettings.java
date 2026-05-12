/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

public record UserSettings(int memberId, boolean notifyNews, boolean notifyNewEvents, boolean notifyEventStatus) {
    public static RowMapping<UserSettings> map() {
        return row -> new UserSettings(
                row.getInt("member_id"),
                row.getBoolean("notify_news"),
                row.getBoolean("notify_new_events"),
                row.getBoolean("notify_event_status"));
    }
}
