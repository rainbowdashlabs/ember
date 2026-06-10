/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.repository;

import dev.chojo.ember.feature.waitinglist.entity.WaitingListEntryStatus;
import dev.chojo.ember.feature.waitinglist.entity.WaitingListFieldConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class WaitingListRepositoryTest extends RepositoryTestBase {
    private int stationId;

    @BeforeEach
    void setUp() {
        var station = stationRepo.create("Test Station " + UUID.randomUUID());
        stationId = station.id();
    }

    @Test
    void createAndFindList() {
        var list =
                waitingListRepo.create(stationId, "Test List", "Description", "[age] * 2", 180, null, null, 5, false);
        assertNotNull(list);
        assertEquals("Test List", list.name());
        assertEquals("[age] * 2", list.scoringFormula());
        assertEquals(180, list.confirmIntervalDays());

        var found = waitingListRepo.findById(list.id());
        assertTrue(found.isPresent());
        assertEquals(list.id(), found.get().id());
    }

    @Test
    void findByStation() {
        waitingListRepo.create(stationId, "List A", "", null, 180, null, null, 5, false);
        waitingListRepo.create(stationId, "List B", "", null, 90, null, null, 5, false);
        var lists = waitingListRepo.findByStation(stationId);
        assertEquals(2, lists.size());
    }

    @Test
    void updateList() {
        var list = waitingListRepo.create(stationId, "Original", "", null, 180, null, null, 5, false);
        var updated = waitingListRepo.update(list.id(), "Updated", "New desc", "[a] + [b]", 90, null, null, 5, false);
        assertTrue(updated.isPresent());
        assertEquals("Updated", updated.get().name());
        assertEquals("[a] + [b]", updated.get().scoringFormula());
    }

    @Test
    void deleteList() {
        var list = waitingListRepo.create(stationId, "To Delete", "", null, 180, null, null, 5, false);
        waitingListRepo.delete(list.id());
        assertTrue(waitingListRepo.findById(list.id()).isEmpty());
    }

    @Test
    void createAndFindFields() {
        var list = waitingListRepo.create(stationId, "List", "", null, 180, null, null, 5, false);
        var field = waitingListRepo.createField(
                list.id(), "Name", "TEXT", WaitingListFieldConfig.parse("{}"), 0, true, true);
        assertNotNull(field);
        assertEquals("Name", field.name());
        assertTrue(field.required());

        var fields = waitingListRepo.findFieldsByList(list.id());
        assertEquals(1, fields.size());
    }

    @Test
    void updateField() {
        var list = waitingListRepo.create(stationId, "List", "", null, 180, null, null, 5, false);
        var field = waitingListRepo.createField(
                list.id(), "Name", "TEXT", WaitingListFieldConfig.parse("{}"), 0, false, true);
        var updated = waitingListRepo.updateField(
                field.id(), "Full Name", "TEXT", WaitingListFieldConfig.parse("{}"), 1, true, true);
        assertTrue(updated.isPresent());
        assertEquals("Full Name", updated.get().name());
        assertTrue(updated.get().required());
    }

    @Test
    void createAndFindInvite() {
        var list = waitingListRepo.create(stationId, "List", "", null, 180, null, null, 5, false);
        String code = UUID.randomUUID().toString();
        var invite = waitingListRepo.createInvite(list.id(), code, 1, null);
        assertEquals(code, invite.code());
        assertEquals(0, invite.uses());
        assertTrue(invite.hasUsesLeft());

        var found = waitingListRepo.findInviteByCode(code);
        assertTrue(found.isPresent());
    }

    @Test
    void incrementInviteUses() {
        var list = waitingListRepo.create(stationId, "List", "", null, 180, null, null, 5, false);
        var invite = waitingListRepo.createInvite(list.id(), UUID.randomUUID().toString(), 1, null);
        waitingListRepo.incrementInviteUses(invite.id());

        var found = waitingListRepo.findInviteByCode(invite.code()).orElseThrow();
        assertEquals(1, found.uses());
        assertFalse(found.hasUsesLeft());
    }

    @Test
    void createAndFindEntry() {
        var list = waitingListRepo.create(stationId, "List", "", null, 180, null, null, 5, false);
        String token = UUID.randomUUID().toString();
        var entry = waitingListRepo.createEntry(list.id(), "Max", "Müller", "Sabine", "test@test.com", token, "Notes");
        assertNotNull(entry);
        assertEquals("Max", entry.firstname());
        assertEquals("Müller", entry.lastname());
        assertEquals(WaitingListEntryStatus.WAITING, entry.status());

        var byToken = waitingListRepo.findEntryByToken(token);
        assertTrue(byToken.isPresent());
        assertEquals(entry.id(), byToken.get().id());
    }

    @Test
    void updateEntryStatus() {
        var list = waitingListRepo.create(stationId, "List", "", null, 180, null, null, 5, false);
        var entry = waitingListRepo.createEntry(
                list.id(),
                "Max",
                "Müller",
                "",
                "test@test.com",
                UUID.randomUUID().toString(),
                "");
        waitingListRepo.updateEntryStatus(entry.id(), WaitingListEntryStatus.JOINED);

        var found = waitingListRepo.findEntryById(entry.id()).orElseThrow();
        assertEquals(WaitingListEntryStatus.JOINED, found.status());
    }

    @Test
    void upsertAndFindEntryValues() {
        var list = waitingListRepo.create(stationId, "List", "", null, 180, null, null, 5, false);
        var field = waitingListRepo.createField(
                list.id(), "Age", "NUMBER", WaitingListFieldConfig.parse("{}"), 0, true, true);
        var entry = waitingListRepo.createEntry(
                list.id(), "Max", "", "", "test@test.com", UUID.randomUUID().toString(), "");

        waitingListRepo.upsertEntryValue(entry.id(), field.id(), "8");
        var values = waitingListRepo.findEntryValues(entry.id());
        assertEquals(1, values.size());
        assertEquals("8", values.getFirst().value());

        // Upsert overwrites
        waitingListRepo.upsertEntryValue(entry.id(), field.id(), "9");
        values = waitingListRepo.findEntryValues(entry.id());
        assertEquals(1, values.size());
        assertEquals("9", values.getFirst().value());
    }

    @Test
    void countEntriesByList() {
        var list = waitingListRepo.create(stationId, "List", "", null, 180, null, null, 5, false);
        assertEquals(0, waitingListRepo.countEntriesByList(list.id()));

        waitingListRepo.createEntry(
                list.id(), "A", "", "", "a@test.com", UUID.randomUUID().toString(), "");
        waitingListRepo.createEntry(
                list.id(), "B", "", "", "b@test.com", UUID.randomUUID().toString(), "");
        assertEquals(2, waitingListRepo.countEntriesByList(list.id()));
    }

    @Test
    void cascadeDeleteListRemovesEntries() {
        var list = waitingListRepo.create(stationId, "List", "", null, 180, null, null, 5, false);
        var field = waitingListRepo.createField(
                list.id(), "Age", "NUMBER", WaitingListFieldConfig.parse("{}"), 0, false, true);
        var entry = waitingListRepo.createEntry(
                list.id(), "Max", "", "", "test@test.com", UUID.randomUUID().toString(), "");
        waitingListRepo.upsertEntryValue(entry.id(), field.id(), "8");
        waitingListRepo.createInvite(list.id(), UUID.randomUUID().toString(), 1, null);

        waitingListRepo.delete(list.id());
        assertTrue(waitingListRepo.findById(list.id()).isEmpty());
        assertTrue(waitingListRepo.findEntriesByList(list.id()).isEmpty());
        assertTrue(waitingListRepo.findFieldsByList(list.id()).isEmpty());
        assertTrue(waitingListRepo.findInvitesByList(list.id()).isEmpty());
    }

    @Test
    void updateConfirmedAtClearsReminder() {
        var list = waitingListRepo.create(stationId, "List", "", null, 180, null, null, 5, false);
        var entry = waitingListRepo.createEntry(
                list.id(), "Max", "", "", "test@test.com", UUID.randomUUID().toString(), "");

        waitingListRepo.updateReminderSentAt(entry.id(), Instant.now());
        var withReminder = waitingListRepo.findEntryById(entry.id()).orElseThrow();
        assertNotNull(withReminder.reminderSentAt());

        waitingListRepo.updateConfirmedAt(entry.id(), Instant.now());
        var confirmed = waitingListRepo.findEntryById(entry.id()).orElseThrow();
        assertNull(confirmed.reminderSentAt());
    }

    @Test
    void deleteField() {
        var list = waitingListRepo.create(stationId, "DelField List", "", null, 180, null, null, 5, false);
        var field = waitingListRepo.createField(
                list.id(), "ToDelete", "TEXT", WaitingListFieldConfig.parse("{}"), 0, false, true);
        waitingListRepo.deleteField(field.id());
        assertTrue(waitingListRepo.findFieldsByList(list.id()).isEmpty());
    }

    @Test
    void deleteInvite() {
        var list = waitingListRepo.create(stationId, "DelInvite List", "", null, 180, null, null, 5, false);
        var invite = waitingListRepo.createInvite(list.id(), UUID.randomUUID().toString(), 5, null);
        waitingListRepo.deleteInvite(invite.id());
        assertTrue(waitingListRepo.findInviteByCode(invite.code()).isEmpty());
    }

    @Test
    void deleteEntry() {
        var list = waitingListRepo.create(stationId, "DelEntry List", "", null, 180, null, null, 5, false);
        var entry = waitingListRepo.createEntry(
                list.id(), "Max", "", "", "test@test.com", UUID.randomUUID().toString(), "");
        waitingListRepo.deleteEntry(entry.id());
        assertTrue(waitingListRepo.findEntryById(entry.id()).isEmpty());
    }

    @Test
    void updateEntryStatusWithTimestamp() {
        var list = waitingListRepo.create(stationId, "StatusTs List", "", null, 180, null, null, 5, false);
        var entry = waitingListRepo.createEntry(
                list.id(), "Max", "", "", "test@test.com", UUID.randomUUID().toString(), "");
        waitingListRepo.updateEntryStatusWithTimestamp(entry.id(), WaitingListEntryStatus.WITHDRAWN, "withdrawn_at");
        var found = waitingListRepo.findEntryById(entry.id()).orElseThrow();
        assertEquals(WaitingListEntryStatus.WITHDRAWN, found.status());
    }

    @Test
    void updateEntry() {
        var list = waitingListRepo.create(stationId, "UpdEntry List", "", null, 180, null, null, 5, false);
        var entry = waitingListRepo.createEntry(
                list.id(),
                "Old",
                "Name",
                "Parent",
                "old@test.com",
                UUID.randomUUID().toString(),
                "old");
        waitingListRepo.updateEntry(entry.id(), "New", "Name", "NewParent", "new@test.com", "new notes");
        var found = waitingListRepo.findEntryById(entry.id()).orElseThrow();
        assertEquals("New", found.firstname());
        assertEquals("new@test.com", found.email());
    }

    @Test
    void linkMember() {
        var list = waitingListRepo.create(stationId, "Link List", "", null, 180, null, null, 5, false);
        var entry = waitingListRepo.createEntry(
                list.id(), "Max", "", "", "test@test.com", UUID.randomUUID().toString(), "");
        // Create a station member to link
        var account = accountRepo.create("wl-link@test.com", "WL", "Link");
        var member = stationMemberRepo.create(stationId, account.id());
        waitingListRepo.linkMember(entry.id(), member.id());
        var found = waitingListRepo.findEntryById(entry.id()).orElseThrow();
        assertEquals(member.id(), found.memberId());
        stationMemberRepo.delete(member.id());
        accountRepo.delete(account.id());
    }

    @Test
    void incrementAttendanceCount() {
        var list = waitingListRepo.create(stationId, "Attendance List", "", null, 180, null, null, 5, false);
        var entry = waitingListRepo.createEntry(
                list.id(), "Max", "", "", "test@test.com", UUID.randomUUID().toString(), "");
        waitingListRepo.incrementAttendanceCount(entry.id());
        var found = waitingListRepo.findEntryById(entry.id()).orElseThrow();
        assertEquals(1, found.attendanceCount());
    }

    @Test
    void findEntriesByStatus() {
        var list = waitingListRepo.create(stationId, "ByStatus List", "", null, 180, null, null, 5, false);
        waitingListRepo.createEntry(
                list.id(), "A", "", "", "a@test.com", UUID.randomUUID().toString(), "");
        var waiting = waitingListRepo.findEntriesByStatus(list.id(), WaitingListEntryStatus.WAITING);
        assertFalse(waiting.isEmpty());
        var joined = waitingListRepo.findEntriesByStatus(list.id(), WaitingListEntryStatus.JOINED);
        assertTrue(joined.isEmpty());
    }

    @Test
    void updateVisibleFields() {
        var list = waitingListRepo.create(stationId, "VisField List", "", null, 180, null, null, 5, false);
        var updated = waitingListRepo.updateVisibleFields(list.id(), "[\"name\"]");
        assertTrue(updated.isPresent());
    }

    @Test
    void updateCreatedAt() {
        var list = waitingListRepo.create(stationId, "CreatedAt List", "", null, 180, null, null, 5, false);
        var entry = waitingListRepo.createEntry(
                list.id(), "Max", "", "", "test@test.com", UUID.randomUUID().toString(), "");
        var newCreatedAt = Instant.parse("2020-06-15T10:00:00Z");
        waitingListRepo.updateCreatedAt(entry.id(), newCreatedAt);
        var found = waitingListRepo.findEntryById(entry.id()).orElseThrow();
        assertNotNull(found.createdAt());
    }

    @Test
    void findAll() {
        // Should return at least the lists we've created
        var all = waitingListRepo.findAll();
        assertFalse(all.isEmpty());
    }
}
