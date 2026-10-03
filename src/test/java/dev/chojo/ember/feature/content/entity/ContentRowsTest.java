/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.content.entity;

import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.feature.restriction.RestrictionAudience;
import dev.chojo.ember.feature.restriction.RestrictionMode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContentRowsTest {

    @Test
    void rowsComeBackAsTheyWereWrittenWithWhoEachBlockIsFor() {
        var audience = new RestrictionAudience(
                List.of(StationUserType.MEMBER), List.of(4), List.of(), List.of(), RestrictionMode.OR);
        var rows = List.of(new ContentRow(
                0,
                0,
                0,
                List.of(
                        new ContentCell(
                                0, 0, 0, 30.0, CellContentType.IMAGE, "logo", CellContentType.IMAGE.emptyConfig()),
                        new ContentCell(
                                0,
                                0,
                                1,
                                70.0,
                                CellContentType.MARKDOWN,
                                "Hallo {{member.firstName}}",
                                CellConfig.EMPTY,
                                audience))));

        var read = ContentRows.read(ContentRows.toJson(rows));

        assertEquals(1, read.size());
        var cells = read.getFirst().cells();
        assertEquals(30.0, cells.getFirst().widthPercent());
        assertEquals("logo", cells.getFirst().content());
        assertNull(cells.getFirst().restriction());
        assertEquals("Hallo {{member.firstName}}", cells.get(1).content());
        assertEquals(audience, cells.get(1).restriction());
    }

    @Test
    void whatCannotBeReadIsLeftOutRatherThanTakingTheRestAlong() {
        var read = ContentRows.read("""
                [{"cells":[{"contentType":"NOT_A_BLOCK"},{"contentType":"MARKDOWN","content":"bleibt",
                  "restriction":"kaputt"},{"contentType":"NESTED_ROWS","config":{"rows":[{"cells":[]}]}}]},
                 {}]""");

        assertEquals(2, read.size());
        var cells = read.getFirst().cells();
        assertEquals(2, cells.size());
        assertEquals("bleibt", cells.getFirst().content());
        assertNull(cells.getFirst().restriction());
        assertEquals(100.0, cells.getFirst().widthPercent());
        assertInstanceOf(CellConfig.NestedRowsConfig.class, cells.get(1).config());
        assertTrue(read.get(1).cells().isEmpty());
        assertTrue(ContentRows.read("not json").isEmpty());
        assertTrue(ContentRows.read((String) null).isEmpty());
    }
}
