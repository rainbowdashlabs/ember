/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.tracking;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TableEntryTest {

    private static TableEntry entry(List<ForeignKey> foreignKeys) {
        return new TableEntry("test", Scope.STATION, null, null, foreignKeys, null, null, null, null, null, null, null);
    }

    @Test
    void listsLeftOutOfTheFileReadAsEmpty() {
        var entry = entry(null);

        assertEquals(List.of(), entry.columns());
        assertEquals(List.of(), entry.foreignKeys());
        assertEquals(List.of(), entry.lookups());
        assertEquals(List.of(), new TransferContext(TrackingStatus.TRACKED, null, null, null).ignoredColumns());
        assertEquals(List.of(), new GdprExportContext(TrackingStatus.TRACKED, null, null, null).identityColumns());
        assertEquals(List.of(), new GdprDeletionContext(TrackingStatus.TRACKED, null, null).strategies());
    }

    @Test
    void foreignKeyForFindsTheKeyOnAColumn() {
        var fk = new ForeignKey("station_id", "station", "id", "CASCADE");

        assertEquals(fk, entry(List.of(fk)).foreignKeyFor("station_id"));
    }

    @Test
    void foreignKeyForNamesTheColumnItCannotFollow() {
        var failure =
                assertThrows(IllegalStateException.class, () -> entry(List.of()).foreignKeyFor("name"));

        assertTrue(failure.getMessage().contains("'name'"), failure.getMessage());
    }
}
