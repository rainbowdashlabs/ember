/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.repository;

import dev.chojo.ember.feature.station.entity.Station;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class StationRepositoryTest extends RepositoryTestBase {
    private static int stationId;

    @Test
    @Order(1)
    void create() {
        Station station = stationRepo.create("Test Station");
        assertNotNull(station);
        assertTrue(station.id() > 0);
        assertEquals("Test Station", station.name());
        stationId = station.id();
    }

    @Test
    @Order(2)
    void findById() {
        assertTrue(stationRepo.findById(stationId).isPresent());
        assertTrue(stationRepo.findById(99999).isEmpty());
    }

    @Test
    @Order(3)
    void findAll() {
        assertFalse(stationRepo.findAll().isEmpty());
    }

    @Test
    @Order(4)
    void update() {
        assertTrue(stationRepo.update(stationId, "Updated Station"));
        assertEquals(
                "Updated Station", stationRepo.findById(stationId).orElseThrow().name());
    }

    @Test
    @Order(5)
    void updateNonExistent() {
        assertFalse(stationRepo.update(99999, "Nope"));
    }

    // -- Logo --

    @Test
    @Order(10)
    void updateLogo() {
        byte[] logo = "fake-png-data".getBytes(StandardCharsets.UTF_8);
        assertTrue(stationRepo.updateLogo(stationId, logo, "image/png"));
    }

    @Test
    @Order(11)
    void findLogo() {
        var logo = stationRepo.findLogo(stationId);
        assertTrue(logo.isPresent());
        assertEquals("image/png", logo.get().contentType());
        assertArrayEquals(
                "fake-png-data".getBytes(StandardCharsets.UTF_8), logo.get().data());
    }

    @Test
    @Order(12)
    void findLogoNotSet() {
        Station other = stationRepo.create("No Logo Station");
        assertTrue(stationRepo.findLogo(other.id()).isEmpty());
        stationRepo.delete(other.id());
    }

    @Test
    @Order(13)
    void deleteLogo() {
        assertTrue(stationRepo.deleteLogo(stationId));
        assertTrue(stationRepo.findLogo(stationId).isEmpty());
    }

    @Test
    @Order(99)
    void delete() {
        assertTrue(stationRepo.delete(stationId));
        assertTrue(stationRepo.findById(stationId).isEmpty());
    }
}
