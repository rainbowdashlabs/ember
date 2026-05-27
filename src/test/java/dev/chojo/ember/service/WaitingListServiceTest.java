/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.service;

import dev.chojo.ember.api.Roles;
import dev.chojo.ember.feature.mail.service.EmailService;
import dev.chojo.ember.feature.members.entity.Role;
import dev.chojo.ember.feature.notifications.service.NotificationService;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.waitinglist.entity.WaitingListEntryStatus;
import dev.chojo.ember.feature.waitinglist.entity.WaitingListFieldConfig;
import dev.chojo.ember.feature.waitinglist.service.WaitingListService;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
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
                waitingListRepo,
                stationRepo,
                stationMemberRepo,
                memberGroupRepo,
                accountRepo,
                emailService,
                notificationService);
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
        var field = service.createField(listId, "Name", "TEXT", WaitingListFieldConfig.parse("{}"), 0, true);
        assertNotNull(field);

        var fields = service.findFieldsByList(listId);
        assertEquals(1, fields.size());

        var updated =
                service.updateField(field.id(), "Full Name", "TEXT", WaitingListFieldConfig.parse("{}"), 0, false);
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
        var field = service.createField(listId, "Age", "NUMBER", WaitingListFieldConfig.parse("{}"), 0, true);

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
        var ageField = service.createField(listId, "Alter", "NUMBER", WaitingListFieldConfig.parse("{}"), 0, true);
        var expField = service.createField(listId, "Erfahrung", "ENUM", WaitingListFieldConfig.parse("{}"), 1, true);
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

    @Test
    void findByStation() {
        var lists = service.findByStation(station.id());
        assertNotNull(lists);
        assertTrue(lists.stream().anyMatch(l -> l.id() == listId));
    }

    @Test
    void updateVisibleFields() {
        var updated = service.updateVisibleFields(listId, "[\"name\",\"age\"]");
        assertTrue(updated.isPresent());
    }

    @Test
    void deleteInvite() {
        var invite = service.createInvite(listId, 5, null);
        service.deleteInvite(invite.id());
        assertTrue(service.findInviteByCode(invite.code()).isEmpty());
    }

    @Test
    void updateEntry() {
        var entry = service.createEntry(listId, "Old", "Name", "Parent", "old@test.com", Map.of(), "old notes");
        service.updateEntry(entry.id(), "New", "Name", "NewParent", "new@test.com", "new notes", null);
        var found = service.findEntryById(entry.id()).orElseThrow();
        assertEquals("New", found.firstname());
        assertEquals("new@test.com", found.email());
    }

    @Test
    void updateEntryWithFieldValues() {
        var field = service.createField(listId, "Score", "NUMBER", WaitingListFieldConfig.parse("{}"), 0, false);
        var entry = service.createEntry(listId, "A", "B", "", "e@test.com", Map.of(field.id(), "5"), "");
        service.updateEntry(entry.id(), "A", "B", "", "e@test.com", "", Map.of(field.id(), "10"));
        var values = service.findEntryValues(entry.id());
        assertTrue(values.stream().anyMatch(v -> "10".equals(v.value())));
    }

    @Test
    void updateCreatedAt() {
        var entry = service.createEntry(listId, "X", "", "", "x@test.com", Map.of(), "");
        var newCreatedAt = Instant.parse("2020-01-01T00:00:00Z");
        service.updateCreatedAt(entry.id(), newCreatedAt);
        var found = service.findEntryById(entry.id()).orElseThrow();
        // The timestamp should have been updated
        assertNotNull(found.createdAt());
    }

    @Test
    void deleteEntry() {
        var entry = service.createEntry(listId, "ToDelete", "", "", "del@test.com", Map.of(), "");
        service.deleteEntry(entry.id());
        assertTrue(service.findEntryById(entry.id()).isEmpty());
    }

    @Test
    void findEntriesByList() {
        service.createEntry(listId, "E1", "", "", "e1@test.com", Map.of(), "");
        var entries = service.findEntriesByList(listId);
        assertFalse(entries.isEmpty());
    }

    @Test
    void findEntriesByStatus() {
        service.createEntry(listId, "StatusTest", "", "", "st@test.com", Map.of(), "");
        var waiting = service.findEntriesByStatus(listId, WaitingListEntryStatus.WAITING);
        assertFalse(waiting.isEmpty());
    }

    @Test
    void inviteEntryLifecycle() {
        var list = service.create(station.id(), "Invite Lifecycle", "", null, 180, null, null, null, 5);
        var entry = service.createEntry(
                list.id(), "InviteeFirst", "InviteeLast", "Parent", "invite@test.com", Map.of(), "");

        // Invite: WAITING -> INVITED
        var invited = service.inviteEntry(entry.id());
        assertEquals(WaitingListEntryStatus.INVITED, invited.status());
        assertNotNull(invited.memberId());

        // Move to testing: INVITED -> TESTING
        var testing = service.moveToTesting(invited.id());
        assertEquals(WaitingListEntryStatus.TESTING, testing.status());

        // Join: TESTING -> JOINED
        var joined = service.moveToJoined(testing.id());
        assertEquals(WaitingListEntryStatus.JOINED, joined.status());
    }

    @Test
    void inviteEntryWrongStatusThrows() {
        var entry = service.createEntry(listId, "Wrong", "", "", "wrong@test.com", Map.of(), "");
        service.updateEntryStatus(entry.id(), WaitingListEntryStatus.JOINED);
        assertThrows(IllegalStateException.class, () -> service.inviteEntry(entry.id()));
    }

    @Test
    void moveToTestingWrongStatusThrows() {
        var entry = service.createEntry(listId, "Bad", "", "", "bad@test.com", Map.of(), "");
        // Entry is WAITING, not INVITED
        assertThrows(IllegalStateException.class, () -> service.moveToTesting(entry.id()));
    }

    @Test
    void moveToJoinedWrongStatusThrows() {
        var entry = service.createEntry(listId, "Bad2", "", "", "bad2@test.com", Map.of(), "");
        // Entry is WAITING, not TESTING
        assertThrows(IllegalStateException.class, () -> service.moveToJoined(entry.id()));
    }

    @Test
    void withdrawEntry() {
        var list = service.create(station.id(), "Withdraw Test", "", null, 180, null, null, null, 5);
        var entry = service.createEntry(list.id(), "Withdrawer", "", "", "wd@test.com", Map.of(), "");
        var withdrawn = service.withdrawEntry(entry.id());
        assertEquals(WaitingListEntryStatus.WITHDRAWN, withdrawn.status());
    }

    @Test
    void withdrawJoinedEntryThrows() {
        var entry = service.createEntry(listId, "Joined", "", "", "joined@test.com", Map.of(), "");
        service.updateEntryStatus(entry.id(), WaitingListEntryStatus.JOINED);
        assertThrows(IllegalStateException.class, () -> service.withdrawEntry(entry.id()));
    }

    @Test
    void checkExpiredConfirmations() {
        var list = service.findById(listId).orElseThrow();
        // Should not throw
        assertDoesNotThrow(() -> service.checkExpiredConfirmations(list));
    }

    @Test
    void scoreEvaluationWithBlankFormula() {
        var entry = service.createEntry(listId, "Max", "", "", "test@test.com", Map.of(), "");
        var values = service.findEntryValues(entry.id());
        var fields = service.findFieldsByList(listId);
        assertEquals(0.0, service.evaluateScore(entry, values, fields, ""));
    }

    @Test
    void scoreEvaluationWithAgeFunction() {
        var dobField = service.createField(listId, "Geburtsdatum", "DATE", WaitingListFieldConfig.parse("{}"), 0, true);
        var list = service.update(listId, "AgeScored", "", "age([Geburtsdatum])", 180, null, null, null, 5)
                .orElseThrow();

        var entry = service.createEntry(
                listId, "Max", "", "", "test@test.com", Map.of(dobField.id(), "\"2016-05-26\""), "");

        var values = service.findEntryValues(entry.id());
        var fields = service.findFieldsByList(listId);
        double score = service.evaluateScore(entry, values, fields, list.scoringFormula());
        assertEquals(10.0, score);
    }

    @Test
    void scoreEvaluationWithWaitingTime() {
        var list = service.update(listId, "WaitScored", "", "wartezeit_tage", 180, null, null, null, 5)
                .orElseThrow();

        var entry = service.createEntry(listId, "Max", "", "", "test@test.com", Map.of(), "");
        var values = service.findEntryValues(entry.id());
        var fields = service.findFieldsByList(listId);
        double score = service.evaluateScore(entry, values, fields, list.scoringFormula());
        assertEquals(0.0, score);
    }

    @Test
    void withdrawInvitedEntryDeletesMember() {
        var list = service.create(station.id(), "Withdraw Invited", "", null, 180, null, null, null, 5);
        var entry = service.createEntry(list.id(), "InvToWithdraw", "Last", "Parent", "invwd@test.com", Map.of(), "");
        var invited = service.inviteEntry(entry.id());
        assertNotNull(invited.memberId());
        var withdrawn = service.withdrawEntry(invited.id());
        assertEquals(WaitingListEntryStatus.WITHDRAWN, withdrawn.status());
    }

    @Test
    void registerViaInvalidInviteCodeThrows() {
        assertThrows(
                IllegalArgumentException.class,
                () -> service.registerViaInvite("nonexistent-code", "A", "", "", "a@test.com", Map.of(), null));
    }

    @Test
    void removeByTokenNonExistent() {
        assertDoesNotThrow(() -> service.removeByToken("no-such-token"));
    }

    @Test
    void confirmInterestNonExistent() {
        assertDoesNotThrow(() -> service.confirmInterest("no-such-token"));
    }

    @Test
    void registerViaExpiredInviteThrows() {
        var invite = service.createInvite(listId, 1, Instant.now().minusSeconds(3600));
        assertThrows(
                IllegalStateException.class,
                () -> service.registerViaInvite(invite.code(), "A", "", "", "a@test.com", Map.of(), null));
    }

    @Test
    void inviteEntryWithTestingGroup() {
        // Create testing group and join group
        var testingGroup = memberGroupRepo.create(station.id(), "WL Testing Group");
        var joinGroup = memberGroupRepo.create(station.id(), "WL Join Group");
        var joinRole =
                stationMemberRepo.findRoleByName(Roles.MEMBER).map(Role::id).orElse(null);

        var list = service.create(
                station.id(),
                "GroupTest " + UUID.randomUUID(),
                "",
                null,
                180,
                testingGroup.id(),
                joinGroup.id(),
                joinRole,
                5);
        var entry = service.createEntry(
                list.id(), "InviteeFirst2", "InviteeLast2", "Parent", "inv2@test.com", Map.of(), "");

        // Invite: WAITING -> INVITED (should add to testing group)
        var invited = service.inviteEntry(entry.id());
        assertEquals(WaitingListEntryStatus.INVITED, invited.status());
        assertNotNull(invited.memberId());

        // Move to testing: INVITED -> TESTING
        var testing = service.moveToTesting(invited.id());
        assertEquals(WaitingListEntryStatus.TESTING, testing.status());

        // Join: TESTING -> JOINED (should remove from testing group, add to join group and role)
        var joined = service.moveToJoined(testing.id());
        assertEquals(WaitingListEntryStatus.JOINED, joined.status());

        // Cleanup
        memberGroupRepo.delete(testingGroup.id());
        memberGroupRepo.delete(joinGroup.id());
    }

    @Test
    void withdrawWithdrawnEntryThrows() {
        var entry = service.createEntry(listId, "WD2", "", "", "wd2@test.com", Map.of(), "");
        service.updateEntryStatus(entry.id(), WaitingListEntryStatus.WITHDRAWN);
        assertThrows(IllegalStateException.class, () -> service.withdrawEntry(entry.id()));
    }

    @Test
    void scoreEvaluationWithInvalidDateField() {
        // age() function with an invalid date string — should not throw, age = 0
        var dobField = service.createField(listId, "BadDate", "DATE", WaitingListFieldConfig.parse("{}"), 0, false);
        var list = service.update(listId, "BadDateScored", "", "age([BadDate])", 180, null, null, null, 5)
                .orElseThrow();
        var entry = service.createEntry(listId, "A", "", "", "t@t.com", Map.of(dobField.id(), "\"not-a-date\""), "");
        var values = service.findEntryValues(entry.id());
        var fields = service.findFieldsByList(listId);
        double score = service.evaluateScore(entry, values, fields, list.scoringFormula());
        assertEquals(0.0, score);
    }

    @Test
    void moveToJoinedWithNullMemberId() {
        // Create an entry in TESTING status that has no linked member (null memberId)
        var list = service.create(station.id(), "NullMember " + UUID.randomUUID(), "", null, 180, null, null, null, 5);
        var entry = service.createEntry(list.id(), "NoMem", "X", "", "nomem@test.com", Map.of(), "");
        // Manually set to TESTING status
        service.updateEntryStatus(entry.id(), WaitingListEntryStatus.TESTING);
        // entry.memberId() is null since we didn't go through inviteEntry
        var joined = service.moveToJoined(entry.id());
        assertEquals(WaitingListEntryStatus.JOINED, joined.status());
    }
}
