/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.content.entity;

import dev.chojo.ember.feature.restriction.RestrictionAudience;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Rows of blocks kept as JSON rather than in the tables of a container: the rows inside a nested-rows
 * block, and the header, footer and body of a letter.
 *
 * <p>Reading is as tolerant as {@link CellConfig#parse(CellContentType, JsonNode)}: a cell of a kind
 * that does not exist is left out, settings that do not fit are read as empty, and a restriction that
 * cannot be read is none. One stale block never takes the rest with it.
 */
public final class ContentRows {
    private static final Logger log = LoggerFactory.getLogger(ContentRows.class);

    private ContentRows() {}

    /**
     * Reads rows written as a JSON array.
     *
     * @param rows the array, or anything else for no rows
     * @return the rows in their stored order
     */
    public static List<ContentRow> read(@Nullable JsonNode rows) {
        if (rows == null || !rows.isArray()) return List.of();
        var out = new ArrayList<ContentRow>();
        int index = 0;
        for (JsonNode row : rows) {
            out.add(new ContentRow(
                    0,
                    0,
                    index++,
                    cells(row.path("cells")),
                    row.path("columnLines").asBoolean(false)));
        }
        return List.copyOf(out);
    }

    /**
     * Reads rows written as JSON text.
     *
     * @param json the text, or anything unreadable for no rows
     * @return the rows
     */
    public static List<ContentRow> read(@Nullable String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return read(CellConfig.MAPPER.readTree(json));
        } catch (JacksonException e) {
            log.error("Failed to read stored rows: {}", json, e);
            return List.of();
        }
    }

    /**
     * @param rows rows of blocks
     * @return the rows as JSON text, the shape {@link #read(String)} takes back
     */
    public static String toJson(List<ContentRow> rows) {
        return CellConfig.MAPPER.writeValueAsString(rows);
    }

    private static List<ContentCell> cells(JsonNode cells) {
        if (!cells.isArray()) return List.of();
        var out = new ArrayList<ContentCell>();
        int index = 0;
        for (JsonNode cell : cells) {
            var type = typeOf(cell.path("contentType"));
            if (type == null) continue;
            out.add(new ContentCell(
                    0,
                    0,
                    index++,
                    cell.path("widthPercent").isNumber()
                            ? cell.path("widthPercent").asDouble()
                            : 100.0,
                    type,
                    text(cell.path("content")),
                    CellConfig.parse(type, cell.path("config")),
                    restriction(cell.path("restriction")),
                    guardianCondition(cell.path("guardianCondition"))));
        }
        return List.copyOf(out);
    }

    private static @Nullable CellContentType typeOf(JsonNode name) {
        if (!name.isString()) return null;
        return Arrays.stream(CellContentType.values())
                .filter(type -> type.name().equals(name.asString()))
                .findFirst()
                .orElse(null);
    }

    private static @Nullable GuardianCondition guardianCondition(JsonNode name) {
        if (!name.isString()) return null;
        return Arrays.stream(GuardianCondition.values())
                .filter(condition -> condition.name().equals(name.asString()))
                .findFirst()
                .orElse(null);
    }

    private static String text(JsonNode node) {
        return node.isString() ? node.asString() : "";
    }

    private static @Nullable RestrictionAudience restriction(JsonNode node) {
        if (!node.isObject()) return null;
        try {
            return CellConfig.MAPPER.treeToValue(node, RestrictionAudience.class);
        } catch (JacksonException e) {
            log.error("Failed to read the restriction of a block: {}", node, e);
            return null;
        }
    }
}
