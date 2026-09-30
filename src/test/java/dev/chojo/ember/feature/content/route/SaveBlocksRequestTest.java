/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.content.route;

import dev.chojo.ember.api.Refusal;
import dev.chojo.ember.api.RefusalResponse;
import dev.chojo.ember.feature.content.entity.CellConfig;
import dev.chojo.ember.feature.content.entity.CellContentType;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The save of a page, news entry or article turns what the editor sent into rows of blocks, and
 * refuses settings a block cannot take instead of saving that block empty.
 */
class SaveBlocksRequestTest {

    private static JsonNode node(String json) {
        return CellConfig.MAPPER.readTree(json);
    }

    private static SaveBlocksRequest oneBlock(String contentType, String content, JsonNode config) {
        return new SaveBlocksRequest(
                List.of(new BlockRowRequest(0, List.of(new BlockCellRequest(0, null, contentType, content, config)))));
    }

    @Test
    void aValidSaveKeepsEveryBlockAsSent() {
        var rows = oneBlock("SPACER", null, node("{\"heightPx\":48}")).toRowData();

        var cell = rows.getFirst().cells().getFirst();
        assertEquals(CellContentType.SPACER, cell.contentType());
        assertEquals(100.0, cell.widthPercent());
        assertEquals("", cell.content());
        assertEquals(new CellConfig.SpacerConfig(48), cell.config());
    }

    @Test
    void absentSettingsAreTheEmptySettingsOfTheKind() {
        var cell = oneBlock("MARKDOWN", "# Hallo", null)
                .toRowData()
                .getFirst()
                .cells()
                .getFirst();

        assertEquals(CellContentType.MARKDOWN.emptyConfig(), cell.config());
        assertEquals("# Hallo", cell.content());
    }

    @Test
    void aWronglyTypedSettingIsRefusedByName() {
        var request = oneBlock("SPACER", null, node("{\"heightPx\":\"abc\"}"));

        var refused = assertThrows(RefusalResponse.class, request::toRowData);

        assertEquals(Refusal.BLOCK_SETTINGS_REJECTED, refused.refusal());
        assertTrue(refused.getMessage().contains("SPACER"), refused.getMessage());
    }

    @Test
    void noRowsAreNoBlocks() {
        assertEquals(List.of(), new SaveBlocksRequest(null).toRowData());
        assertEquals(
                List.of(),
                BlockRowRequest.toRowData(List.of(new BlockRowRequest(0, null)))
                        .getFirst()
                        .cells());
    }
}
