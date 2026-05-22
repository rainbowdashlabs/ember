/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.waitinglist.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

public record WaitingListEntryValue(int entryId, int fieldId, String value) {

    public static RowMapping<WaitingListEntryValue> map() {
        return row -> new WaitingListEntryValue(row.getInt("entry_id"), row.getInt("field_id"), row.getString("value"));
    }
}
