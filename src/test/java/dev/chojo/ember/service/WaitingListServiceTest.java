/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.service;

import dev.chojo.ember.feature.mail.service.EmailService;
import dev.chojo.ember.feature.notifications.service.NotificationService;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.waitinglist.entity.WaitingListEntryStatus;
import dev.chojo.ember.feature.waitinglist.service.WaitingListService;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WaitingListServiceTest extends RepositoryTestBase {
    private static WaitingListService service;
    private static Station station;
    private int listId;

    @BeforeAll
    static void setup() {
        var emailService = mock(EmailService.class);
        var notificationService = mock(NotificationService.class);
        service = new WaitingListService(
                waitingListRepo, stationRepo, stationMemberRepo, memberGroupRepo, emailService, notificationService);
        station = stationRepo.create("WaitlistStation");
    }

    @BeforeEach
    void createList() {
        var list = service.create(station.id(), "Test " + UUID.randomUUID(), "", null, 180, null, null, null, 5);
        listId = list.id();
    }

    @Test
    void createAndFindList() {
        var list = service.create(station.id(), "New List", "Description", "[age] * 2", 90, null, null, null, 5);
        var found = service.findById(list.id());
        assertTrue(found.isPresent());
        assertEquals("New List", found.get().name());
        assertEquals("[age] * 2", found.get().scoringFormula());
    }

    @Test
    void updateList() {
        var updated = service.update(listId, "Updated", "New desc", "[a]", 60, null, null, null, 5);
        assertTrue(updated.isPresent());
        assertEquals("Updated", updated.get().name());
    }

    @Test
    void deleteList() {
        service.delete(listId);
        assertTrue(service.findById(listId).isEmpty());
    }

    @Test
    void fieldCrud() {
        var field = service.createField(listId, "Name", "TEXT", "{}", 0, true);
        assertNotNull(field);

        var fields = service.findFieldsByList(listId);
        assertEquals(1, fields.size());

        var updated = service.updateField(field.id(), "Full Name", "TEXT", "{}", 0, false);
        assertTrue(updated.isPresent());
        assertEquals("Full Name", updated.get().name());

        service.deleteField(field.id());
        assertTrue(service.findFieldsByList(listId).isEmpty());
    }

    @Test
    void inviteCreation() {
        var invite = service.createInvite(listId, 3, null);
        assertNotNull(invite);
        assertTrue(invite.hasUsesLeft());
        assertFalse(invite.isExpired());

        var invites = service.findInvitesByList(listId);
        assertEquals(1, invites.size());
    }

    @Test
    void registerViaInvite() {
        var invite = service.createInvite(listId, 1, null);
        var field = service.createField(listId, "Age", "NUMBER", "{}", 0, true);

        var entry = service.registerViaInvite(
                invite.code(), "Max", "Müller", "Sabine", "test@test.com", Map.of(field.id(), "8"), "Test note");

        assertNotNull(entry);
        assertEquals("Max", entry.firstname());
        assertEquals("Müller", entry.lastname());
        assertEquals(WaitingListEntryStatus.WAITING, entry.status());

        // Invite should be used up
        var usedInvite = service.findInviteByCode(invite.code()).orElseThrow();
        assertFalse(usedInvite.hasUsesLeft());

        // Values should be stored
        var values = service.findEntryValues(entry.id());
        assertEquals(1, values.size());
        assertEquals("8", values.getFirst().value());
    }

    @Test
    void registerViaInviteRejectsUsedUpCode() {
        var invite = service.createInvite(listId, 1, null);
        service.registerViaInvite(invite.code(), "A", "", "", "a@test.com", Map.of(), null);

        assertThrows(
                IllegalStateException.class,
                () -> service.registerViaInvite(invite.code(), "B", "", "", "b@test.com", Map.of(), null));
    }

    @Test
    void manualEntryCreation() {
        var entry = service.createEntry(listId, "Child", "Last", "Parent", "email@test.com", Map.of(), "Notes");
        assertNotNull(entry);

        var found = service.findEntryById(entry.id());
        assertTrue(found.isPresent());
    }

    @Test
    void updateEntryStatus() {
        var entry = service.createEntry(listId, "Child", "Last", "", "e@test.com", Map.of(), "");
        service.updateEntryStatus(entry.id(), WaitingListEntryStatus.JOINED);

        var found = service.findEntryById(entry.id()).orElseThrow();
        assertEquals(WaitingListEntryStatus.JOINED, found.status());
    }

    @Test
    void selfServiceByToken() {
        var invite = service.createInvite(listId, 1, null);
        var entry = service.registerViaInvite(invite.code(), "Max", "", "", "test@test.com", Map.of(), null);

        // Find by token
        var found = service.findEntryByToken(entry.accessToken());
        assertTrue(found.isPresent());

        // Confirm interest
        service.confirmInterest(entry.accessToken());
        var confirmed = service.findEntryByToken(entry.accessToken()).orElseThrow();
        assertNotNull(confirmed.confirmedAt());

        // Remove (now withdraw)
        service.removeByToken(entry.accessToken());
        var removed = service.findEntryByToken(entry.accessToken()).orElseThrow();
        assertEquals(WaitingListEntryStatus.WITHDRAWN, removed.status());
    }

    @Test
    void scoreEvaluation() {
        var ageField = service.createField(listId, "Alter", "NUMBER", "{}", 0, true);
        var expField = service.createField(listId, "Erfahrung", "ENUM", "{}", 1, true);
        var list = service.update(
                        listId,
                        "Scored",
                        "",
                        "[Alter] * (\"[Erfahrung]\" == \"fortgeschritten\" ? 2 : 1)",
                        180,
                        null,
                        null,
                        null,
                        5)
                .orElseThrow();

        var entry = service.createEntry(
                listId,
                "Max",
                "",
                "",
                "test@test.com",
                Map.of(ageField.id(), "10", expField.id(), "\"fortgeschritten\""),
                "");

        var values = service.findEntryValues(entry.id());
        var fields = service.findFieldsByList(listId);
        double score = service.evaluateScore(entry, values, fields, list.scoringFormula());
        assertEquals(20.0, score);
    }

    @Test
    void scoreEvaluationWithNoFormula() {
        var entry = service.createEntry(listId, "Max", "", "", "test@test.com", Map.of(), "");
        var values = service.findEntryValues(entry.id());
        var fields = service.findFieldsByList(listId);
        assertEquals(0.0, service.evaluateScore(entry, values, fields, null));
    }

    @Test
    void countEntries() {
        assertEquals(0, service.countEntries(listId));
        service.createEntry(listId, "A", "", "", "a@test.com", Map.of(), "");
        service.createEntry(listId, "B", "", "", "b@test.com", Map.of(), "");
        assertEquals(2, service.countEntries(listId));
    }
}
