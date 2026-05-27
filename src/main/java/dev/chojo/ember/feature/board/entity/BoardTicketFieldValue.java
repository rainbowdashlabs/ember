/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.board.entity;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import de.chojo.sadu.mapper.rowmapper.RowMapping;
import org.slf4j.Logger;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import static org.slf4j.LoggerFactory.getLogger;

/**
 * A field value for a board ticket. The value is stored as JSONB wrapping any scalar or list.
 *
 * @param ticketId the ticket this value belongs to
 * @param fieldId  the board field definition
 * @param value    the value as a parsed object (string, number, boolean, list, or null)
 */
public record BoardTicketFieldValue(int ticketId, int fieldId, Object value) {
    private static final Logger log = getLogger(BoardTicketFieldValue.class);
    private static final ObjectMapper MAPPER = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .changeDefaultVisibility(v -> v.withFieldVisibility(JsonAutoDetect.Visibility.ANY)
                    .withGetterVisibility(JsonAutoDetect.Visibility.NONE))
            .build();

    public String valueToJson() {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (Exception e) {
            return "null";
        }
    }

    public static Object parseValue(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            return MAPPER.readValue(json, Object.class);
        } catch (Exception e) {
            log.error("Failed to parse field value: {}", json, e);
            return null;
        }
    }

    public static RowMapping<BoardTicketFieldValue> map() {
        return row -> new BoardTicketFieldValue(
                row.getInt("ticket_id"), row.getInt("field_id"), parseValue(row.getString("value")));
    }
}
