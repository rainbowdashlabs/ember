/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.service;

import dev.chojo.ember.feature.account.service.AuthService;
import dev.chojo.ember.feature.federation.service.FederationService;
import dev.chojo.ember.feature.station.entity.StationModule;
import dev.chojo.ember.feature.station.service.StationService;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class StationServiceTest extends RepositoryTestBase {
    private static StationService service;
    private static int stationId;

    @BeforeAll
    static void setup() {
        service = new StationService(
                stationRepo, stationMemberRepo, accountRepo, mock(AuthService.class), mock(FederationService.class));
    }

    @Test
    @Order(1)
    void create() {
        var station = service.create("TestStation");
        assertNotNull(station);
        assertEquals("TestStation", station.name());
        stationId = station.id();
    }

    @Test
    @Order(2)
    void findById() {
        assertTrue(service.findById(stationId).isPresent());
        assertTrue(service.findById(999999).isEmpty());
    }

    @Test
    @Order(3)
    void findAll() {
        var all = service.findAll();
        assertTrue(all.stream().anyMatch(s -> s.id() == stationId));
    }

    @Test
    @Order(10)
    void update() {
        var updated = service.update(stationId, "RenamedStation");
        assertTrue(updated.isPresent());
        assertEquals("RenamedStation", updated.get().name());
    }

    @Test
    @Order(11)
    void updateTimezone() {
        var updated = service.updateTimezone(stationId, "Europe/Berlin");
        assertTrue(updated.isPresent());
        assertEquals("Europe/Berlin", updated.get().timezone());
    }

    @Test
    @Order(12)
    void updateLocale() {
        var updated = service.updateLocale(stationId, "en-US");
        assertTrue(updated.isPresent());
        assertEquals("en-US", updated.get().locale());
    }

    @Test
    @Order(20)
    void disableAndEnableModule() {
        service.setDisabledModules(stationId, Set.of(StationModule.ATTENDANCE));
        var disabled = service.findDisabledModules(stationId);
        assertTrue(disabled.contains(StationModule.ATTENDANCE));
        assertFalse(service.isModuleEnabled(stationId, StationModule.ATTENDANCE));

        service.setDisabledModules(stationId, Set.of());
        var enabled = service.findDisabledModules(stationId);
        assertFalse(enabled.contains(StationModule.ATTENDANCE));
        assertTrue(service.isModuleEnabled(stationId, StationModule.ATTENDANCE));
    }

    @Test
    @Order(30)
    void delete() {
        assertTrue(service.delete(stationId));
        assertTrue(service.findById(stationId).isEmpty());
    }
}
