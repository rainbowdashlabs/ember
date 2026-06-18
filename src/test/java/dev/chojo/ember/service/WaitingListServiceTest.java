/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.service;

import de.chojo.sadu.queries.api.call.Call;
import de.chojo.sadu.queries.api.query.Query;
import dev.chojo.ember.auth.PasswordHasher;
import dev.chojo.ember.event.DomainEventBus;
import dev.chojo.ember.feature.legal.entity.ConsentProof;
import dev.chojo.ember.feature.mail.service.EmailService;
import dev.chojo.ember.feature.notifications.service.NotificationService;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.waitinglist.entity.GuardianInput;
import dev.chojo.ember.feature.waitinglist.entity.WaitingListEntryStatus;
import dev.chojo.ember.feature.waitinglist.entity.WaitingListFieldConfig;
import dev.chojo.ember.feature.waitinglist.entity.WaitingListFieldType;
import dev.chojo.ember.feature.waitinglist.service.WaitingListService;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.node.IntNode;
import tools.jackson.databind.node.StringNode;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WaitingListServiceTest extends RepositoryTestBase {
    private static final ConsentProof TEST_CONSENT =
            new ConsentProof("c", "p", "t", "127.0.0.1", "DE", "test-agent", Instant.now());

    private static WaitingListService service;
    private static Station station;
    private int listId;

    private static List<GuardianInput> guardians(String name, String email) {
        return List.of(new GuardianInput(name, "", email, ""));
    }

    @BeforeAll
    static void setup() {
        var emailService = mock(EmailService.class);
        var notificationService = mock(NotificationService.class);
        var passwordHasher = mock(PasswordHasher.class);
        when(passwordHasher.hash(anyString())).thenReturn("$test$hash");
        service = new WaitingListService(
                waitingListRepo,
                stationRepo,
                stationMemberRepo,
                memberGroupRepo,
                accountRepo,
                emailService,
                notificationService,
                passwordHasher,
                new DomainEventBus(Set.of()));
        station = stationRepo.create("WaitlistStation");
    }

    @BeforeEach
    void createList() {
        var list = service.create(station.id(), "Test " + UUID.randomUUID(), "", null, 180, null, null, 5, false);
        listId = list.id();
    }

    @Test
    void createAndFindList() {
        var list = service.create(station.id(), "New List", "Description", "[age] * 2", 90, null, null, 5, false);
        var found = service.findById(list.id());
        assertTrue(found.isPresent());
        assertEquals("New List", found.get().name());
        assertEquals("[age] * 2", found.get().scoringFormula());
    }

    @Test
    void updateList() {
        var updated = service.update(listId, "Updated", "New desc", "[a]", 60, null, null, 5, false);
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
        var field = service.createField(
                listId, "Name", WaitingListFieldType.TEXT, WaitingListFieldConfig.parse("{}"), 0, true, true);
        assertNotNull(field);

        var fields = service.findFieldsByList(listId);
        assertEquals(1, fields.size());

        var updated = service.updateField(
                field.id(), "Full Name", WaitingListFieldType.TEXT, WaitingListFieldConfig.parse("{}"), 0, false, true);
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
        var field = service.createField(
                listId, "Age", WaitingListFieldType.NUMBER, WaitingListFieldConfig.parse("{}"), 0, true, true);

        var entry = service.registerViaInvite(
                invite.code(),
                "Max",
                "Müller",
                guardians("Sabine", "test@test.com"),
                Map.of(field.id(), IntNode.valueOf(8)),
                "Test note",
                TEST_CONSENT);

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
        assertEquals(IntNode.valueOf(8), values.getFirst().value());
    }

    @Test
    void registerViaInviteRejectsUsedUpCode() {
        var invite = service.createInvite(listId, 1, null);
        service.registerViaInvite(invite.code(), "A", "", guardians("", "a@test.com"), Map.of(), null, TEST_CONSENT);

        assertThrows(
                IllegalStateException.class,
                () -> service.registerViaInvite(
                        invite.code(), "B", "", guardians("", "b@test.com"), Map.of(), null, TEST_CONSENT));
    }

    @Test
    void manualEntryCreation() {
        var entry =
                service.createEntry(listId, "Child", "Last", guardians("Parent", "email@test.com"), Map.of(), "Notes");
        assertNotNull(entry);

        var found = service.findEntryById(entry.id());
        assertTrue(found.isPresent());
    }

    @Test
    void updateEntryStatus() {
        var entry = service.createEntry(listId, "Child", "Last", guardians("", "e@test.com"), Map.of(), "");
        service.updateEntryStatus(entry.id(), WaitingListEntryStatus.JOINED);

        var found = service.findEntryById(entry.id()).orElseThrow();
        assertEquals(WaitingListEntryStatus.JOINED, found.status());
    }

    @Test
    void selfServiceByToken() {
        var invite = service.createInvite(listId, 1, null);
        var entry = service.registerViaInvite(
                invite.code(), "Max", "", guardians("", "test@test.com"), Map.of(), null, TEST_CONSENT);

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
        var ageField = service.createField(
                listId, "Alter", WaitingListFieldType.NUMBER, WaitingListFieldConfig.parse("{}"), 0, true, true);
        var expField = service.createField(
                listId, "Erfahrung", WaitingListFieldType.ENUM, WaitingListFieldConfig.parse("{}"), 1, true, true);
        var list = service.update(
                        listId,
                        "Scored",
                        "",
                        "[Alter] * (\"[Erfahrung]\" == \"fortgeschritten\" ? 2 : 1)",
                        180,
                        null,
                        null,
                        5,
                        false)
                .orElseThrow();

        var entry = service.createEntry(
                listId,
                "Max",
                "",
                guardians("", "test@test.com"),
                Map.of(ageField.id(), IntNode.valueOf(10), expField.id(), StringNode.valueOf("fortgeschritten")),
                "");

        var values = service.findEntryValues(entry.id());
        var fields = service.findFieldsByList(listId);
        double score = service.evaluateScore(entry, values, fields, list.scoringFormula());
        assertEquals(20.0, score);
    }

    @Test
    void scoreEvaluationWithNoFormula() {
        var entry = service.createEntry(listId, "Max", "", guardians("", "test@test.com"), Map.of(), "");
        var values = service.findEntryValues(entry.id());
        var fields = service.findFieldsByList(listId);
        assertEquals(0.0, service.evaluateScore(entry, values, fields, null));
    }

    @Test
    void countEntries() {
        assertEquals(0, service.countEntries(listId));
        service.createEntry(listId, "A", "", guardians("", "a@test.com"), Map.of(), "");
        service.createEntry(listId, "B", "", guardians("", "b@test.com"), Map.of(), "");
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
        var entry =
                service.createEntry(listId, "Old", "Name", guardians("Parent", "old@test.com"), Map.of(), "old notes");
        service.updateEntry(entry.id(), "New", "Name", guardians("NewParent", "new@test.com"), "new notes", null);
        var found = service.findEntryById(entry.id()).orElseThrow();
        assertEquals("New", found.firstname());
        assertEquals("new@test.com", found.email());
    }

    @Test
    void updateEntryWithFieldValues() {
        var field = service.createField(
                listId, "Score", WaitingListFieldType.NUMBER, WaitingListFieldConfig.parse("{}"), 0, false, true);
        var entry = service.createEntry(
                listId, "A", "B", guardians("", "e@test.com"), Map.of(field.id(), IntNode.valueOf(5)), "");
        service.updateEntry(
                entry.id(), "A", "B", guardians("", "e@test.com"), "", Map.of(field.id(), IntNode.valueOf(10)));
        var values = service.findEntryValues(entry.id());
        assertTrue(values.stream().anyMatch(v -> IntNode.valueOf(10).equals(v.value())));
    }

    @Test
    void updateCreatedAt() {
        var entry = service.createEntry(listId, "X", "", guardians("", "x@test.com"), Map.of(), "");
        var newCreatedAt = Instant.parse("2020-01-01T00:00:00Z");
        service.updateCreatedAt(entry.id(), newCreatedAt);
        var found = service.findEntryById(entry.id()).orElseThrow();
        // The timestamp should have been updated
        assertNotNull(found.createdAt());
    }

    @Test
    void deleteEntry() {
        var entry = service.createEntry(listId, "ToDelete", "", guardians("", "del@test.com"), Map.of(), "");
        service.deleteEntry(entry.id());
        assertTrue(service.findEntryById(entry.id()).isEmpty());
    }

    @Test
    void findEntriesByList() {
        service.createEntry(listId, "E1", "", guardians("", "e1@test.com"), Map.of(), "");
        var entries = service.findEntriesByList(listId);
        assertFalse(entries.isEmpty());
    }

    @Test
    void findEntriesByStatus() {
        service.createEntry(listId, "StatusTest", "", guardians("", "st@test.com"), Map.of(), "");
        var waiting = service.findEntriesByStatus(listId, WaitingListEntryStatus.WAITING);
        assertFalse(waiting.isEmpty());
    }

    @Test
    void inviteEntryLifecycle() {
        var list = service.create(station.id(), "Invite Lifecycle", "", null, 180, null, null, 5, false);
        var entry = service.createEntry(
                list.id(), "InviteeFirst", "InviteeLast", guardians("Parent", "invite@test.com"), Map.of(), "");

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
        var entry = service.createEntry(listId, "Wrong", "", guardians("", "wrong@test.com"), Map.of(), "");
        service.updateEntryStatus(entry.id(), WaitingListEntryStatus.JOINED);
        assertThrows(IllegalStateException.class, () -> service.inviteEntry(entry.id()));
    }

    @Test
    void moveToTestingWrongStatusThrows() {
        var entry = service.createEntry(listId, "Bad", "", guardians("", "bad@test.com"), Map.of(), "");
        // Entry is WAITING, not INVITED
        assertThrows(IllegalStateException.class, () -> service.moveToTesting(entry.id()));
    }

    @Test
    void moveToJoinedWrongStatusThrows() {
        var entry = service.createEntry(listId, "Bad2", "", guardians("", "bad2@test.com"), Map.of(), "");
        // Entry is WAITING, not TESTING
        assertThrows(IllegalStateException.class, () -> service.moveToJoined(entry.id()));
    }

    @Test
    void withdrawEntry() {
        var list = service.create(station.id(), "Withdraw Test", "", null, 180, null, null, 5, false);
        var entry = service.createEntry(list.id(), "Withdrawer", "", guardians("", "wd@test.com"), Map.of(), "");
        service.withdrawEntry(entry.id());
        assertTrue(service.findEntryById(entry.id()).isEmpty());
    }

    @Test
    void withdrawJoinedEntryThrows() {
        var entry = service.createEntry(listId, "Joined", "", guardians("", "joined@test.com"), Map.of(), "");
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
        var entry = service.createEntry(listId, "Max", "", guardians("", "test@test.com"), Map.of(), "");
        var values = service.findEntryValues(entry.id());
        var fields = service.findFieldsByList(listId);
        assertEquals(0.0, service.evaluateScore(entry, values, fields, ""));
    }

    @Test
    void scoreEvaluationWithAgeFunction() {
        var dobField = service.createField(
                listId, "Geburtsdatum", WaitingListFieldType.DATE, WaitingListFieldConfig.parse("{}"), 0, true, true);
        var list = service.update(listId, "AgeScored", "", "age([Geburtsdatum])", 180, null, null, 5, false)
                .orElseThrow();

        var entry = service.createEntry(
                listId,
                "Max",
                "",
                guardians("", "test@test.com"),
                Map.of(dobField.id(), StringNode.valueOf("2016-05-26")),
                "");

        var values = service.findEntryValues(entry.id());
        var fields = service.findFieldsByList(listId);
        double score = service.evaluateScore(entry, values, fields, list.scoringFormula());
        assertEquals(10.0, score);
    }

    @Test
    void scoreEvaluationWithWaitingTime() {
        var list = service.update(listId, "WaitScored", "", "wartezeit_tage", 180, null, null, 5, false)
                .orElseThrow();

        var entry = service.createEntry(listId, "Max", "", guardians("", "test@test.com"), Map.of(), "");
        var values = service.findEntryValues(entry.id());
        var fields = service.findFieldsByList(listId);
        double score = service.evaluateScore(entry, values, fields, list.scoringFormula());
        assertEquals(0.0, score);
    }

    @Test
    void withdrawInvitedEntryDeletesMember() {
        var list = service.create(station.id(), "Withdraw Invited", "", null, 180, null, null, 5, false);
        var entry = service.createEntry(
                list.id(), "InvToWithdraw", "Last", guardians("Parent", "invwd@test.com"), Map.of(), "");
        var invited = service.inviteEntry(entry.id());
        assertNotNull(invited.memberId());
        service.withdrawEntry(invited.id());
        assertTrue(service.findEntryById(invited.id()).isEmpty());
    }

    @Test
    void findGuardiansByEntry() {
        var entry = service.createEntry(
                listId,
                "GuardianTest",
                "Last",
                List.of(
                        new GuardianInput("Parent1", "", "p1@test.com", "123"),
                        new GuardianInput("Parent2", "", "p2@test.com", "")),
                Map.of(),
                "");
        var guardians = service.findGuardiansByEntry(entry.id());
        assertEquals(2, guardians.size());
        assertTrue(guardians.stream().anyMatch(g -> g.email().equals("p1@test.com")));
        assertTrue(guardians.stream().anyMatch(g -> g.email().equals("p2@test.com")));
    }

    @Test
    void findGuardiansByList() {
        service.createEntry(
                listId, "ListGuardian1", "", List.of(new GuardianInput("G1", "", "lg1@test.com", "")), Map.of(), "");
        service.createEntry(
                listId, "ListGuardian2", "", List.of(new GuardianInput("G2", "", "lg2@test.com", "")), Map.of(), "");
        var guardians = service.findGuardiansByList(listId);
        assertTrue(guardians.size() >= 2);
    }

    @Test
    void registerViaInvalidInviteCodeThrows() {
        assertThrows(
                IllegalArgumentException.class,
                () -> service.registerViaInvite(
                        "nonexistent-code", "A", "", guardians("", "a@test.com"), Map.of(), null, TEST_CONSENT));
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
                () -> service.registerViaInvite(
                        invite.code(), "A", "", guardians("", "a@test.com"), Map.of(), null, TEST_CONSENT));
    }

    @Test
    void inviteEntryWithTestingGroup() {
        // Create testing group and join group
        var testingGroup = memberGroupRepo.create(station.id(), "WL Testing Group");
        var joinGroup = memberGroupRepo.create(station.id(), "WL Join Group");
        var list = service.create(
                station.id(),
                "GroupTest " + UUID.randomUUID(),
                "",
                null,
                180,
                testingGroup.id(),
                joinGroup.id(),
                5,
                false);
        var entry = service.createEntry(
                list.id(), "InviteeFirst2", "InviteeLast2", guardians("Parent", "inv2@test.com"), Map.of(), "");

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
    void moveToJoinedCreatesGuardianAccounts() {
        var list =
                service.create(station.id(), "GuardianAcct " + UUID.randomUUID(), "", null, 180, null, null, 5, false);
        var entry = service.createEntry(
                list.id(),
                "Child",
                "Name",
                List.of(new GuardianInput(
                        "Max", "Mustermann", "guardian-new-" + UUID.randomUUID() + "@test.com", "123")),
                Map.of(),
                "");

        // Go through the full lifecycle: invite -> testing -> joined
        var invited = service.inviteEntry(entry.id());
        var testing = service.moveToTesting(invited.id());
        var joined = service.moveToJoined(testing.id());
        assertEquals(WaitingListEntryStatus.JOINED, joined.status());
    }

    @Test
    void moveToJoinedWithExistingGuardianAccount() {
        // Pre-create an account with the guardian's email
        var existingAccount = accountRepo.create("existing-guardian@test.com", "Existing", "Guardian");
        var existingMember = stationMemberRepo.create(station.id(), existingAccount.id());

        var list =
                service.create(station.id(), "ExistGuardian " + UUID.randomUUID(), "", null, 180, null, null, 5, false);
        var entry = service.createEntry(
                list.id(),
                "Child2",
                "Name",
                List.of(new GuardianInput("Existing", "Guardian", "existing-guardian@test.com", "")),
                Map.of(),
                "");

        var invited = service.inviteEntry(entry.id());
        var testing = service.moveToTesting(invited.id());
        var joined = service.moveToJoined(testing.id());
        assertEquals(WaitingListEntryStatus.JOINED, joined.status());

        // Cleanup
        stationMemberRepo.delete(existingMember.id());
        accountRepo.delete(existingAccount.id());
    }

    @Test
    void moveToJoinedGuardianWithBlankEmail() {
        var list =
                service.create(station.id(), "BlankGuardian " + UUID.randomUUID(), "", null, 180, null, null, 5, false);
        var entry = service.createEntry(
                list.id(), "Child3", "Name", List.of(new GuardianInput("NoEmail", "Guardian", "", "")), Map.of(), "");

        var invited = service.inviteEntry(entry.id());
        var testing = service.moveToTesting(invited.id());
        var joined = service.moveToJoined(testing.id());
        assertEquals(WaitingListEntryStatus.JOINED, joined.status());
    }

    @Test
    void withdrawWithdrawnEntryThrows() {
        var entry = service.createEntry(listId, "WD2", "", guardians("", "wd2@test.com"), Map.of(), "");
        service.updateEntryStatus(entry.id(), WaitingListEntryStatus.WITHDRAWN);
        assertThrows(IllegalStateException.class, () -> service.withdrawEntry(entry.id()));
    }

    @Test
    void scoreEvaluationWithInvalidDateField() {
        // age() function with an invalid date string — should not throw, age = 0
        var dobField = service.createField(
                listId, "BadDate", WaitingListFieldType.DATE, WaitingListFieldConfig.parse("{}"), 0, false, true);
        var list = service.update(listId, "BadDateScored", "", "age([BadDate])", 180, null, null, 5, false)
                .orElseThrow();
        var entry = service.createEntry(
                listId, "A", "", guardians("", "t@t.com"), Map.of(dobField.id(), StringNode.valueOf("not-a-date")), "");
        var values = service.findEntryValues(entry.id());
        var fields = service.findFieldsByList(listId);
        double score = service.evaluateScore(entry, values, fields, list.scoringFormula());
        assertEquals(0.0, score);
    }

    @Test
    void moveToJoinedWithNullMemberId() {
        // Create an entry in TESTING status that has no linked member (null memberId)
        var list = service.create(station.id(), "NullMember " + UUID.randomUUID(), "", null, 180, null, null, 5, false);
        var entry = service.createEntry(list.id(), "NoMem", "X", guardians("", "nomem@test.com"), Map.of(), "");
        // Manually set to TESTING status
        service.updateEntryStatus(entry.id(), WaitingListEntryStatus.TESTING);
        // entry.memberId() is null since we didn't go through inviteEntry
        var joined = service.moveToJoined(entry.id());
        assertEquals(WaitingListEntryStatus.JOINED, joined.status());
    }

    // --- Public waitlist tests ---

    @Test
    void findPublicByStation() {
        // listId is created with isPublic=false in @BeforeEach
        var publicList =
                service.create(station.id(), "Public " + UUID.randomUUID(), "", null, 180, null, null, 5, true);
        var publicLists = service.findPublicByStation(station.id());
        assertTrue(publicLists.stream().anyMatch(l -> l.id() == publicList.id()));
        assertTrue(publicLists.stream().noneMatch(l -> l.id() == listId));
    }

    @Test
    void hasPublicWaitlists() {
        // Use a fresh station so no prior public lists interfere
        var freshStation = stationRepo.create("HasPublicTest " + UUID.randomUUID());
        assertFalse(service.hasPublicWaitlists(freshStation.id()));
        service.create(freshStation.id(), "PublicHas " + UUID.randomUUID(), "", null, 180, null, null, 5, true);
        assertTrue(service.hasPublicWaitlists(freshStation.id()));
    }

    @Test
    void findPublicFieldsByList() {
        var publicField = service.createField(
                listId, "PubField", WaitingListFieldType.TEXT, WaitingListFieldConfig.parse("{}"), 0, false, true);
        var privateField = service.createField(
                listId, "PrivField", WaitingListFieldType.TEXT, WaitingListFieldConfig.parse("{}"), 1, false, false);
        var publicFields = service.findPublicFieldsByList(listId);
        assertTrue(publicFields.stream().anyMatch(f -> f.id() == publicField.id()));
        assertTrue(publicFields.stream().noneMatch(f -> f.id() == privateField.id()));
    }

    @Test
    void submitAndVerifyPublicRegistration() {
        var publicList =
                service.create(station.id(), "PubReg " + UUID.randomUUID(), "", null, 180, null, null, 5, true);
        service.createField(
                publicList.id(), "Age", WaitingListFieldType.NUMBER, WaitingListFieldConfig.parse("{}"), 0, true, true);

        service.submitPublicRegistration(
                publicList.id(),
                "TestChild",
                "Last",
                "verify-test@test.com",
                List.of(new GuardianInput("Parent", "Last", "parent@test.com", "")),
                Map.of(),
                "notes",
                TEST_CONSENT);

        // Retrieve the token from the repository (via the verification table)
        var token = waitingListRepo.findPublicByStation(station.id()).stream()
                .flatMap(l -> {
                    // We know a token was created — find it
                    return Stream.empty();
                })
                .findFirst();

        // Verify via the DB directly
        var tokens = Query.query("SELECT token FROM waitlist_verification_token WHERE list_id = :list_id")
                .single(Call.of().bind("list_id", publicList.id()))
                .map(row -> row.getString("token"))
                .all();
        assertFalse(tokens.isEmpty());

        boolean verified = service.verifyPublicRegistration(tokens.getFirst());
        assertTrue(verified);

        // Entry should exist with PENDING status
        var entries = service.findEntriesByStatus(publicList.id(), WaitingListEntryStatus.PENDING);
        assertEquals(1, entries.size());
        assertEquals("TestChild", entries.getFirst().firstname());
    }

    @Test
    void approvePendingEntry() {
        var publicList =
                service.create(station.id(), "Approve " + UUID.randomUUID(), "", null, 180, null, null, 5, true);
        // Create a PENDING entry directly
        var entry = waitingListRepo.createEntryWithStatus(
                publicList.id(),
                "Pending",
                "Child",
                "",
                "pending@test.com",
                UUID.randomUUID().toString(),
                "",
                WaitingListEntryStatus.PENDING,
                null);

        var approved = service.approvePendingEntry(entry.id());
        assertEquals(WaitingListEntryStatus.WAITING, approved.status());
    }

    @Test
    void rejectPendingEntry() {
        var publicList =
                service.create(station.id(), "Reject " + UUID.randomUUID(), "", null, 180, null, null, 5, true);
        var entry = waitingListRepo.createEntryWithStatus(
                publicList.id(),
                "Reject",
                "Child",
                "",
                "reject@test.com",
                UUID.randomUUID().toString(),
                "",
                WaitingListEntryStatus.PENDING,
                null);

        service.rejectPendingEntry(entry.id());
        assertTrue(service.findEntryById(entry.id()).isEmpty());
    }

    @Test
    void submitPublicRegistrationNonPublicListThrows() {
        // listId is not public
        assertThrows(
                IllegalStateException.class,
                () -> service.submitPublicRegistration(
                        listId, "Test", "", "t@t.com", List.of(), Map.of(), "", TEST_CONSENT));
    }

    @Test
    void verifyPublicRegistrationInvalidToken() {
        assertFalse(service.verifyPublicRegistration("nonexistent-token"));
    }

    @Test
    void submitPublicRegistrationListNotFoundThrows() {
        assertThrows(
                IllegalArgumentException.class,
                () -> service.submitPublicRegistration(
                        99999, "A", "", "a@a.com", List.of(), Map.of(), "", TEST_CONSENT));
    }

    @Test
    void findPublicFieldsByListReturnsOnlyPublic() {
        var pub = service.createField(
                listId, "PubF2", WaitingListFieldType.TEXT, WaitingListFieldConfig.parse("{}"), 10, false, true);
        var priv = service.createField(
                listId, "PrivF2", WaitingListFieldType.TEXT, WaitingListFieldConfig.parse("{}"), 11, false, false);
        var pubFields = service.findPublicFieldsByList(listId);
        assertTrue(pubFields.stream().anyMatch(f -> f.id() == pub.id()));
        assertFalse(pubFields.stream().anyMatch(f -> f.id() == priv.id()));
    }

    @Test
    void approveNonPendingThrows() {
        var entry = service.createEntry(listId, "NotPending", "", guardians("", "np@test.com"), Map.of(), "");
        assertThrows(IllegalStateException.class, () -> service.approvePendingEntry(entry.id()));
    }

    @Test
    void rejectNonPendingThrows() {
        var entry = service.createEntry(listId, "NotPending2", "", guardians("", "np2@test.com"), Map.of(), "");
        assertThrows(IllegalStateException.class, () -> service.rejectPendingEntry(entry.id()));
    }
}
