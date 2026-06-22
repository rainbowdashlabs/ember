/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.station.service;

import dev.chojo.ember.feature.mail.service.EmailService;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static dev.chojo.ember.feature.station.entity.ApplicationStatus.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class StationApplicationServiceTest extends RepositoryTestBase {
    private static StationApplicationService service;
    private static int applicationId;
    private static String verificationToken;

    @BeforeAll
    static void setup() {
        var emailService = mock(EmailService.class);
        service = new StationApplicationService(
                stationApplicationRepo, stationRepo, accountRepo, stationMemberRepo, emailService);
    }

    @Test
    @Order(1)
    void submit() {
        var app = service.submit("Max", "Mustermann", "app-max@test.com", "Max Station", "Hello!");
        assertNotNull(app);
        assertEquals("Max", app.firstName());
        assertEquals("Max Station", app.stationName());
        assertEquals(UNVERIFIED, app.status());
        assertNotNull(app.verificationToken());
        applicationId = app.id();
        verificationToken = app.verificationToken();
    }

    @Test
    @Order(2)
    void findById() {
        var found = service.findById(applicationId);
        assertTrue(found.isPresent());
        assertEquals("Max", found.get().firstName());
    }

    @Test
    @Order(3)
    void findByIdNotFound() {
        assertTrue(service.findById(99999).isEmpty());
    }

    @Test
    @Order(4)
    void findPending() {
        // Not yet verified — not pending
        var pending = service.findPending();
        assertTrue(pending.stream().noneMatch(a -> a.id() == applicationId));
    }

    @Test
    @Order(5)
    void findAll() {
        var all = service.findAll();
        assertFalse(all.isEmpty());
        assertTrue(all.stream().anyMatch(a -> a.id() == applicationId));
    }

    @Test
    @Order(10)
    void verifyWithValidToken() {
        assertTrue(service.verify(verificationToken));
        var app = service.findById(applicationId).orElseThrow();
        assertEquals(PENDING, app.status());
    }

    @Test
    @Order(11)
    void verifyWithInvalidToken() {
        assertFalse(service.verify("nonexistent-token-xyz"));
    }

    @Test
    @Order(12)
    void findPendingAfterVerify() {
        var pending = service.findPending();
        assertTrue(pending.stream().anyMatch(a -> a.id() == applicationId));
    }

    @Test
    @Order(20)
    void denyNonPending() {
        // Submit and verify a second application
        var app2 = service.submit("Anna", "Schmidt", "app-anna@test.com", "Anna Station", "Hi!");
        stationApplicationRepo.verify(app2.id());

        // Deny it
        var denied = service.deny(app2.id(), "Not suitable");
        assertEquals(DENIED, denied.status());
        assertEquals("Not suitable", denied.denyReason());
    }

    @Test
    @Order(21)
    void denyAlreadyPending() {
        // applicationId is still pending — deny it here
        var denied = service.deny(applicationId, "Duplicate request");
        assertEquals(DENIED, denied.status());
    }

    @Test
    @Order(30)
    void acceptCreatesStationAndAccount() {
        // Create a fresh application and verify it
        var app = service.submit("Lena", "Muster", "app-lena@test.com", "Lena Station", "");
        stationApplicationRepo.verify(app.id());

        var accepted = service.accept(app.id());
        assertEquals(ACCEPTED, accepted.status());

        // The station should now exist
        var stations = stationRepo.findAll();
        assertTrue(stations.stream().anyMatch(s -> "Lena Station".equals(s.name())));

        // Cleanup created station
        stations.stream()
                .filter(s -> "Lena Station".equals(s.name()))
                .findFirst()
                .ifPresent(s -> stationRepo.delete(s.id()));
    }

    @Test
    @Order(31)
    void acceptNotFound() {
        assertThrows(IllegalArgumentException.class, () -> service.accept(99999));
    }

    @Test
    @Order(32)
    void denyNotFound() {
        assertThrows(IllegalArgumentException.class, () -> service.deny(99999, "reason"));
    }
}
