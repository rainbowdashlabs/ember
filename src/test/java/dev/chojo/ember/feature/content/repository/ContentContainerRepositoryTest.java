/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.content.repository;

import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.content.entity.CellConfig;
import dev.chojo.ember.feature.content.entity.CellContentType;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ContentContainerRepositoryTest extends RepositoryTestBase {

    private static Station station;
    private static Account account;

    @BeforeAll
    static void setupClass() {
        station = stationRepo.create("ContentContainerStation");
        account = accountRepo.create("content-container@test.com", "Content", "Container");
        stationMemberRepo.create(station.id(), account.id());
    }

    @AfterAll
    static void cleanupClass() {
        stationRepo.delete(station.id());
        accountRepo.delete(account.id());
    }

    @Test
    void createAndFind() {
        var container = contentContainerRepo.create(station.id());
        try {
            assertEquals(station.id(), container.stationId());
            assertNotNull(container.createdAt());
            assertTrue(contentContainerRepo.findById(container.id()).isPresent());
            assertTrue(contentContainerRepo.findById(99999).isEmpty());
        } finally {
            contentContainerRepo.delete(container.id());
        }
        assertTrue(contentContainerRepo.findById(container.id()).isEmpty());
        assertFalse(contentContainerRepo.delete(99999));
    }

    @Test
    void rowsAndCellsAreReadBackInReadingOrder() {
        var container = contentContainerRepo.create(station.id());
        try {
            int rowId = contentContainerRepo.insertRow(container.id(), 0);
            assertTrue(rowId > 0);
            contentContainerRepo.insertCell(
                    rowId, 0, 60.0, CellContentType.MARKDOWN, "<h1>Hello</h1>", CellConfig.EMPTY);
            contentContainerRepo.insertCell(
                    rowId,
                    1,
                    40.0,
                    CellContentType.IMAGE,
                    "abc123",
                    new CellConfig.ImageConfig(
                            CellConfig.ImageFit.COVER, null, null, null, null, null, null, null, null, null, null));

            var rows = contentContainerRepo.findRows(container.id());
            assertEquals(1, rows.size());
            assertEquals(container.id(), rows.getFirst().containerId());

            var cells = contentContainerRepo.findCellsByRow(rowId);
            assertEquals(2, cells.size());
            assertEquals(CellContentType.MARKDOWN, cells.getFirst().contentType());
            assertEquals(60.0, cells.getFirst().widthPercent());

            var loaded = contentContainerRepo.loadRows(container.id());
            assertEquals(1, loaded.size());
            assertEquals(2, loaded.getFirst().cells().size());
            assertEquals(2, contentContainerRepo.findAllCells(container.id()).size());
        } finally {
            contentContainerRepo.delete(container.id());
        }
    }

    @Test
    void deletingTheRowsLeavesTheContainer() {
        var container = contentContainerRepo.create(station.id());
        try {
            int rowId = contentContainerRepo.insertRow(container.id(), 0);
            contentContainerRepo.insertCell(rowId, 0, 100.0, CellContentType.MARKDOWN, "txt", CellConfig.EMPTY);
            contentContainerRepo.deleteRows(container.id());
            assertEquals(0, contentContainerRepo.findRows(container.id()).size());
            assertTrue(contentContainerRepo.findById(container.id()).isPresent());
        } finally {
            contentContainerRepo.delete(container.id());
        }
    }

    @Test
    void deletingTheContainerTakesItsBlocksWithIt() {
        var container = contentContainerRepo.create(station.id());
        int rowId = contentContainerRepo.insertRow(container.id(), 0);
        contentContainerRepo.insertCell(rowId, 0, 100.0, CellContentType.MARKDOWN, "txt", CellConfig.EMPTY);

        assertTrue(contentContainerRepo.delete(container.id()));
        assertTrue(contentContainerRepo.findCellsByRow(rowId).isEmpty());
    }

    @Test
    void everyCellOfTheStationIsFoundWhateverOwnsIt() {
        var container = contentContainerRepo.create(station.id());
        try {
            int rowId = contentContainerRepo.insertRow(container.id(), 0);
            contentContainerRepo.insertCell(rowId, 0, 100.0, CellContentType.IMAGE, "station-wide", CellConfig.EMPTY);

            assertTrue(contentContainerRepo.findAllCellsByStation(station.id()).stream()
                    .anyMatch(c -> "station-wide".equals(c.content())));
        } finally {
            contentContainerRepo.delete(container.id());
        }
    }
}
