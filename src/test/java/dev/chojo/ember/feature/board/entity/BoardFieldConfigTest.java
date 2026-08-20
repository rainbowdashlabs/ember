/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.board.entity;

import dev.chojo.ember.util.Json;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * The settings of a board field arrive as an object and are bound once the field type says which
 * record they are. Reading them from the tree rather than from JSON text is what keeps the request
 * and the field in step.
 */
class BoardFieldConfigTest {

    private static JsonNode node(String json) {
        return Json.CONFIG_MAPPER.readTree(json);
    }

    @Test
    void bindsAnObjectAgainstTheTypeBesideIt() {
        var config = BoardFieldConfig.parse(BoardFieldType.ENUM, node("{\"required\":true,\"options\":[\"a\",\"b\"]}"));

        var asEnum = assertInstanceOf(BoardFieldConfig.Enum.class, config);
        assertEquals(List.of("a", "b"), asEnum.options());
        assertEquals(true, asEnum.required());
    }

    @Test
    void answersTheEmptySettingsOfTheTypeWhenNoneAreNamed() {
        assertEquals(
                BoardFieldConfig.empty(BoardFieldType.STRING),
                BoardFieldConfig.parse(BoardFieldType.STRING, (JsonNode) null));
        assertEquals(
                BoardFieldConfig.empty(BoardFieldType.ENUM), BoardFieldConfig.parse(BoardFieldType.ENUM, node("null")));
    }

    /** A tree that does not fit the type is the field's own settings gone, not the request refused. */
    @Test
    void fallsBackToTheEmptySettingsWhenTheTreeDoesNotFit() {
        assertEquals(
                BoardFieldConfig.empty(BoardFieldType.ENUM),
                BoardFieldConfig.parse(BoardFieldType.ENUM, node("{\"options\":\"not a list\"}")));
    }

    /** Reading a field back out of the database still starts from the text the column holds. */
    @Test
    void stillReadsTheTextTheColumnHolds() {
        var config = BoardFieldConfig.parse(BoardFieldType.STRING, "{\"required\":true}");

        assertEquals(true, config.required());
    }
}
