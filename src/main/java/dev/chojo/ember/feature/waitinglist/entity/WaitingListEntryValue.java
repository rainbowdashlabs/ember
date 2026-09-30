/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.waitinglist.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;
import dev.chojo.ember.util.Json;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;

public record WaitingListEntryValue(int entryId, int fieldId, JsonNode value) {

    public static RowMapping<WaitingListEntryValue> map() {
        return row -> new WaitingListEntryValue(
                row.getInt("entry_id"), row.getInt("field_id"), nodeOrEmpty(row.getString("value")));
    }

    private static JsonNode nodeOrEmpty(String json) {
        if (json == null || json.isBlank()) return Json.MAPPER.createObjectNode();
        try {
            return Json.MAPPER.readTree(json);
        } catch (JacksonException e) {
            return Json.MAPPER.createObjectNode();
        }
    }
}
