/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.page.entity;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * The settings of a cell arrive as an object and are bound once the content type says which record
 * they are. Reading them from the tree rather than from JSON text is what keeps the request and the
 * cell in step.
 */
class CellConfigTest {

    private static JsonNode node(String json) {
        return CellConfig.MAPPER.readTree(json);
    }

    @Test
    void bindsAnObjectAgainstTheTypeBesideIt() {
        var config = CellConfig.parse(CellContentType.VIDEO, node("{\"autoplay\":true,\"loop\":false}"));

        var video = assertInstanceOf(CellConfig.VideoConfig.class, config);
        assertEquals(true, video.autoplay());
        assertEquals(false, video.loop());
    }

    @Test
    void answersTheEmptySettingsOfTheTypeWhenNoneAreNamed() {
        assertEquals(CellContentType.VIDEO.emptyConfig(), CellConfig.parse(CellContentType.VIDEO, (JsonNode) null));
        assertEquals(CellContentType.VIDEO.emptyConfig(), CellConfig.parse(CellContentType.VIDEO, node("null")));
        assertEquals(CellContentType.VIDEO.emptyConfig(), CellConfig.parse(CellContentType.VIDEO, node("{}")));
    }

    /** A tree that does not fit the type is the cell's own settings gone, not the request refused. */
    @Test
    void fallsBackToTheEmptySettingsWhenTheTreeDoesNotFit() {
        assertEquals(
                CellContentType.VIDEO.emptyConfig(),
                CellConfig.parse(CellContentType.VIDEO, node("{\"autoplay\":\"not a flag\"}")));
    }

    /** Reading a cell back out of the database still starts from the text the column holds. */
    @Test
    void stillReadsTheTextTheColumnHolds() {
        var config = CellConfig.parse(CellContentType.VIDEO, "{\"autoplay\":true}");

        assertEquals(
                true, assertInstanceOf(CellConfig.VideoConfig.class, config).autoplay());
    }
}
