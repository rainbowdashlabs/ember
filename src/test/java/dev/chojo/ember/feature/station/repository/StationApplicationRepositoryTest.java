/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.station.repository;

import dev.chojo.ember.feature.station.entity.ApplicationStatus;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.UUID;

import static dev.chojo.ember.feature.station.entity.ApplicationStatus.*;
import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class StationApplicationRepositoryTest extends RepositoryTestBase {
    private static int applicationId;
    private static String token;

    @Test
    @Order(1)
    void create() {
        token = UUID.randomUUID().toString();
        var app = stationApplicationRepo.create("Max", "Mustermann", "max@test.com", "Test Station", "Hello!", token);
        assertNotNull(app);
        assertEquals("Max", app.firstName());
        assertEquals("Test Station", app.stationName());
        assertEquals(UNVERIFIED, app.status());
        applicationId = app.id();
    }

    @Test
    @Order(2)
    void findById() {
        assertTrue(stationApplicationRepo.findById(applicationId).isPresent());
        assertTrue(stationApplicationRepo.findById(99999).isEmpty());
    }

    @Test
    @Order(3)
    void findByToken() {
        var app = stationApplicationRepo.findByToken(token);
        assertTrue(app.isPresent());
        assertEquals(applicationId, app.get().id());
    }

    @Test
    @Order(4)
    void findByTokenNotFound() {
        assertTrue(stationApplicationRepo.findByToken("nonexistent").isEmpty());
    }

    @Test
    @Order(5)
    void findByStatus() {
        var unverified = stationApplicationRepo.findByStatus(ApplicationStatus.UNVERIFIED);
        assertFalse(unverified.isEmpty());
    }

    @Test
    @Order(6)
    void findAll() {
        assertFalse(stationApplicationRepo.findAll().isEmpty());
    }

    @Test
    @Order(10)
    void verify() {
        assertTrue(stationApplicationRepo.verify(applicationId));
        assertEquals(
                PENDING,
                stationApplicationRepo.findById(applicationId).orElseThrow().status());
    }

    @Test
    @Order(11)
    void verifyAlreadyVerified() {
        assertFalse(stationApplicationRepo.verify(applicationId));
    }

    @Test
    @Order(12)
    void accept() {
        assertTrue(stationApplicationRepo.accept(applicationId));
        assertEquals(
                ACCEPTED,
                stationApplicationRepo.findById(applicationId).orElseThrow().status());
    }

    @Test
    @Order(13)
    void acceptAlreadyAccepted() {
        assertFalse(stationApplicationRepo.accept(applicationId));
    }

    @Test
    @Order(20)
    void denyFlow() {
        // Create a new application for the deny flow
        var app = stationApplicationRepo.create(
                "Anna",
                "Schmidt",
                "anna@test.com",
                "Another Station",
                "Hi!",
                UUID.randomUUID().toString());
        stationApplicationRepo.verify(app.id());
        assertTrue(stationApplicationRepo.deny(app.id(), "Not accepted"));
        var denied = stationApplicationRepo.findById(app.id()).orElseThrow();
        assertEquals(DENIED, denied.status());
        assertEquals("Not accepted", denied.denyReason());
    }

    @Test
    @Order(21)
    void denyAlreadyDenied() {
        // Cannot deny a non-pending application
        assertFalse(stationApplicationRepo.deny(applicationId, "Too late"));
    }
}
