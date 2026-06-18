/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.waitinglist.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;
import dev.chojo.ember.util.JsonUtil;
import tools.jackson.databind.JsonNode;

public record WaitingListEntryValue(int entryId, int fieldId, JsonNode value) {

    public static RowMapping<WaitingListEntryValue> map() {
        return row -> new WaitingListEntryValue(
                row.getInt("entry_id"), row.getInt("field_id"), JsonUtil.parseNode(row.getString("value")));
    }
}
