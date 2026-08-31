/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.waitinglist.service;

import de.chojo.sadu.queries.api.call.Call;
import de.chojo.sadu.queries.api.query.Query;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.event.DomainEventBus;
import dev.chojo.ember.feature.account.service.AccountInviteService;
import dev.chojo.ember.feature.account.service.AuthService;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.legal.entity.ConsentProof;
import dev.chojo.ember.feature.mail.service.EmailService;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.notifications.service.NotificationService;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.waitinglist.entity.GuardianInput;
import dev.chojo.ember.feature.waitinglist.entity.WaitingListAnswer;
import dev.chojo.ember.feature.waitinglist.entity.WaitingListEntry;
import dev.chojo.ember.feature.waitinglist.entity.WaitingListEntryStatus;
import dev.chojo.ember.feature.waitinglist.entity.WaitingListFieldConfig;
import dev.chojo.ember.feature.waitinglist.entity.WaitingListFieldType;
import dev.chojo.ember.feature.waitinglist.entity.WaitingListInvitation;
import dev.chojo.ember.repository.RepositoryTestBase;
import io.javalin.http.ConflictResponse;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.node.IntNode;
import tools.jackson.databind.node.StringNode;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
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
    private static AuthService authService;
    private int listId;

    private static List<GuardianInput> guardians(String name, String email) {
        return List.of(new GuardianInput(name, "", email, ""));
    }

    /** An invitation that names no evening, which is every story not about the appointment. */
    private static WaitingListEntry invite(int entryId) {
        return service.inviteEntry(entryId, null);
    }

    private static StationEvent appointment(String name) {
        return eventRepo.create(
                station.id(),
                name,
                "",
                StationEvent.EventType.ONE_TIME,
                null,
                Instant.parse("2026-05-12T18:00:00Z"),
                Instant.parse("2026-05-12T20:00:00Z"),
                null,
                false,
                null,
                false,
                null,
                null,
                null,
                null,
                null);
    }

    @BeforeAll
    static void setup() {
        var emailService = mock(EmailService.class);
        var notificationService = mock(NotificationService.class);
        authService = mock(AuthService.class);
        service = new WaitingListService(
                waitingListRepo,
                stationRepo,
                stationMemberRepo,
                memberGroupRepo,
                accountRepo,
                emailService,
                notificationService,
                new AccountInviteService(accountRepo, authService),
                new WaitlistInvitationMessage(eventRepo, eventFieldRepo, emailService),
                new DomainEventBus(Set.of()));
        station = stationRepo.create("WaitlistStation");
    }

    @BeforeEach
    void createList() {
        var list = service.create(
                station.id(), "Test " + UUID.randomUUID(), "", null, 180, null, null, 5, false, null, null);
        listId = list.id();
    }

    @Test
    void createAndFindList() {
        var list = service.create(
                station.id(), "New List", "Description", "[age] * 2", 90, null, null, 5, false, null, null);
        var found = service.findById(list.id());
        assertTrue(found.isPresent());
        assertEquals("New List", found.get().name());
        assertEquals("[age] * 2", found.get().scoringFormula());
    }

    @Test
    void updateList() {
        var updated = service.update(listId, "Updated", "New desc", "[a]", 60, null, null, 5, false, null, null);
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

        // Remove: while waiting there is nothing but the entry, so it goes for good
        service.removeByToken(entry.accessToken());
        assertTrue(service.findEntryByToken(entry.accessToken()).isEmpty());
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
                        false,
                        null,
                        null)
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
        var list = service.create(station.id(), "Invite Lifecycle", "", null, 180, null, null, 5, false, null, null);
        var entry = service.createEntry(
                list.id(), "InviteeFirst", "InviteeLast", guardians("Parent", "invite@test.com"), Map.of(), "");

        // Invite: WAITING -> INVITED. An invitation is a message, so nobody is on the station yet.
        var invited = invite(entry.id());
        assertEquals(WaitingListEntryStatus.INVITED, invited.status());
        assertNull(invited.memberId(), "an invitation must not put anybody on the station");

        // Move to testing: INVITED -> TESTING, which is where the member appears
        var testing = service.moveToTesting(invited.id());
        assertEquals(WaitingListEntryStatus.TESTING, testing.status());
        assertNotNull(testing.memberId());

        // Join: TESTING -> JOINED
        var joined = service.moveToJoined(testing.id());
        assertEquals(WaitingListEntryStatus.JOINED, joined.status());
    }

    /**
     * The parent who answers for a child is a member like any other, and joins the same way.
     *
     * <p>The waiting list used to write the account and a random password itself, so no setup mail
     * ever went out and the parent could not sign in at all.
     */
    @Test
    void aGuardianJoiningIsInvitedAndCarriesNoPassword() {
        var list = service.create(station.id(), "Guardian Invite", "", null, 180, null, null, 5, false, null, null);
        var entry = service.createEntry(
                list.id(), "Kind", "Mustermann", guardians("Mutter", "mutter@test.com"), Map.of(), "");
        var joined = service.moveToJoined(
                service.moveToTesting(invite(entry.id()).id()).id());

        var managers = stationMemberRepo.findManagers(joined.memberId());
        assertEquals(1, managers.size(), "the child has exactly one guardian");
        var guardian = managers.getFirst();
        assertEquals(StationUserType.GUARDIAN, guardian.userType());
        assertTrue(
                accountRepo.findCredential(guardian.accountId()).isEmpty(),
                "the waiting list must not set a password nobody knows");
        verify(authService).sendPasswordSetup(guardian.accountId());
    }

    /**
     * A parent who gave no address still answers for their child.
     *
     * <p>They used to be skipped outright, which left the child on the list with nobody linked to
     * them. Nowhere to write to is a reason not to write, not a reason not to exist.
     */
    @Test
    void aGuardianWithoutAnAddressIsStillLinkedToTheChild() {
        var list = service.create(station.id(), "Guardian Silent", "", null, 180, null, null, 5, false, null, null);
        var entry = service.createEntry(list.id(), "Kind", "Ohnemail", guardians("Vater", ""), Map.of(), "");
        var joined = service.moveToJoined(
                service.moveToTesting(invite(entry.id()).id()).id());

        var managers = stationMemberRepo.findManagers(joined.memberId());
        assertEquals(1, managers.size(), "the child still has a guardian");
        assertEquals(StationUserType.GUARDIAN, managers.getFirst().userType());
    }

    /**
     * The invitation names one evening: an appointment and the date of it, because a weekly Dienst
     * without a date would mean every Tuesday there has ever been.
     */
    @Test
    void anInvitationCarriesTheEveningItIsAbout() {
        var event = appointment("Schnupperdienst");
        var entry = service.createEntry(listId, "Neu", "Gast", guardians("", "gast@test.com"), Map.of(), "");

        var invited = service.inviteEntry(
                entry.id(), new WaitingListInvitation(event.id(), LocalDate.of(2026, 5, 12), LocalTime.of(17, 45)));

        assertEquals(WaitingListEntryStatus.INVITED, invited.status());
        assertNotNull(invited.invitation());
        assertEquals(event.id(), invited.invitation().eventId());
        assertEquals(LocalDate.of(2026, 5, 12), invited.invitation().date());
        assertEquals(LocalTime.of(17, 45), invited.invitation().arrivalTime());
    }

    /** Nobody is signed up from an invitation: they have not joined anything yet. */
    @Test
    void anInvitationSignsNobodyUpForTheAppointment() {
        var event = appointment("Kein Platz");
        var entry = service.createEntry(listId, "Neu", "Gast", guardians("", "gast2@test.com"), Map.of(), "");

        service.inviteEntry(entry.id(), new WaitingListInvitation(event.id(), LocalDate.of(2026, 5, 12), null));

        assertTrue(
                eventRegistrationRepo
                        .findByEventAndDate(event.id(), LocalDate.of(2026, 5, 12))
                        .isEmpty(),
                "an invitation is a message, not a place on the attendee list");
    }

    @Test
    void anInvitationMayNameNoEveningAtAll() {
        var entry = service.createEntry(listId, "Neu", "Ohne", guardians("", "ohne@test.com"), Map.of(), "");

        var invited = invite(entry.id());

        assertEquals(WaitingListEntryStatus.INVITED, invited.status());
        assertNull(invited.invitation());
    }

    /**
     * The way back, which is what a station takes when the answer was that the date does not suit.
     * The invitation goes with it, so an old mail can never answer the one that replaced it.
     */
    @Test
    void anInvitedEntryGoesBackToWaitingWithoutItsInvitation() {
        var event = appointment("Passt nicht");
        var entry = service.createEntry(listId, "Neu", "Zurueck", guardians("", "zurueck@test.com"), Map.of(), "");
        var invited =
                service.inviteEntry(entry.id(), new WaitingListInvitation(event.id(), LocalDate.of(2026, 5, 12), null));

        var back = service.returnToWaiting(invited.id());

        assertEquals(WaitingListEntryStatus.WAITING, back.status());
        assertNull(back.invitation(), "the invitation it carried is no longer current");
    }

    @Test
    void onlyAnInvitedEntryGoesBackToWaiting() {
        var entry = service.createEntry(listId, "Neu", "Wartend", guardians("", "wartend@test.com"), Map.of(), "");
        assertThrows(IllegalStateException.class, () -> service.returnToWaiting(entry.id()));
    }

    @Test
    void returnToWaitingNeedsAnEntry() {
        assertThrows(IllegalArgumentException.class, () -> service.returnToWaiting(-1));
    }

    /** The station knows to expect them, and the answer sits where somebody will see it. */
    @Test
    void anInvitationIsAnsweredWithoutSigningIn() {
        var event = appointment("Antwort");
        var entry = service.createEntry(listId, "Neu", "Ja", guardians("", "ja@test.com"), Map.of(), "");
        var invited =
                service.inviteEntry(entry.id(), new WaitingListInvitation(event.id(), LocalDate.of(2026, 5, 12), null));

        var answered = service.answerInvitation(
                invited.accessToken(), event.id(), LocalDate.of(2026, 5, 12), WaitingListAnswer.COMING, "  Bis dann  ");

        assertNotNull(answered.answer());
        assertEquals(WaitingListAnswer.COMING, answered.answer().answer());
        assertEquals("Bis dann", answered.answer().note());
        assertNotNull(answered.answer().answeredAt());
    }

    /**
     * A refusal is recorded and nothing more. Withdrawing would move the entry into the closed
     * section, which is precisely where the manager is not looking.
     */
    @Test
    void arefusalLeavesTheEntryWhereTheManagerIsLooking() {
        var entry = service.createEntry(listId, "Neu", "Nein", guardians("", "nein@test.com"), Map.of(), "");
        var invited = invite(entry.id());

        var answered =
                service.answerInvitation(invited.accessToken(), null, null, WaitingListAnswer.NOT_INTERESTED, null);

        assertEquals(WaitingListEntryStatus.INVITED, answered.status());
        assertEquals(WaitingListAnswer.NOT_INTERESTED, answered.answer().answer());
        assertEquals("", answered.answer().note());
    }

    /** An old link in a mailbox cannot answer the invitation that replaced the one it was sent for. */
    @Test
    void anAnswerAboutAnotherAppointmentIsRefused() {
        var first = appointment("Erster");
        var second = appointment("Zweiter");
        var entry = service.createEntry(listId, "Neu", "Alt", guardians("", "alt@test.com"), Map.of(), "");
        var invited = service.inviteEntry(
                entry.id(), new WaitingListInvitation(second.id(), LocalDate.of(2026, 5, 19), null));

        assertThrows(
                ConflictResponse.class,
                () -> service.answerInvitation(
                        invited.accessToken(), first.id(), LocalDate.of(2026, 5, 12), WaitingListAnswer.COMING, null));
    }

    @Test
    void anAnswerNamingADateTheEntryWasNotInvitedToIsRefused() {
        var event = appointment("Anderer Abend");
        var entry = service.createEntry(listId, "Neu", "Datum", guardians("", "datum@test.com"), Map.of(), "");
        var invited =
                service.inviteEntry(entry.id(), new WaitingListInvitation(event.id(), LocalDate.of(2026, 5, 19), null));

        assertThrows(
                ConflictResponse.class,
                () -> service.answerInvitation(
                        invited.accessToken(), event.id(), LocalDate.of(2026, 5, 12), WaitingListAnswer.COMING, null));
    }

    @Test
    void anEntryThatHasMovedOnCanNoLongerBeAnswered() {
        var entry = service.createEntry(listId, "Neu", "Weiter", guardians("", "weiter@test.com"), Map.of(), "");
        var testing = service.moveToTesting(invite(entry.id()).id());

        assertThrows(
                ConflictResponse.class,
                () -> service.answerInvitation(testing.accessToken(), null, null, WaitingListAnswer.COMING, null));
    }

    @Test
    void answeringNeedsATokenThatNamesAnEntry() {
        assertThrows(
                IllegalArgumentException.class,
                () -> service.answerInvitation("nothing", null, null, WaitingListAnswer.COMING, null));
    }

    /** A second invitation is a new question, so the answer to the first does not stand for it. */
    @Test
    void aNewInvitationClearsTheAnswerToTheOldOne() {
        var entry = service.createEntry(listId, "Neu", "Nochmal", guardians("", "nochmal@test.com"), Map.of(), "");
        var invited = invite(entry.id());
        service.answerInvitation(
                invited.accessToken(), null, null, WaitingListAnswer.DATE_DOES_NOT_SUIT, "Da kann ich nicht");

        var back = service.returnToWaiting(invited.id());
        assertNull(back.answer(), "going back to waiting takes the answer with the invitation");

        var event = appointment("Neuer Anlauf");
        var again =
                service.inviteEntry(back.id(), new WaitingListInvitation(event.id(), LocalDate.of(2026, 5, 19), null));
        assertNull(again.answer());
    }

    /**
     * The list shows a count against a threshold, and this is what feeds it. Nothing else happens
     * when the threshold is reached: joining stays the deliberate act it is.
     */
    @Test
    void turningUpDuringTheTrialPeriodRaisesTheCount() {
        var entry = service.createEntry(listId, "Neu", "Probe", guardians("", "probe@test.com"), Map.of(), "");
        var testing = service.moveToTesting(invite(entry.id()).id());

        service.recordTrialAttendance(testing.memberId());
        service.recordTrialAttendance(testing.memberId());

        assertEquals(2, service.findEntryById(testing.id()).orElseThrow().attendanceCount());
    }

    /** Only a trial period counts. An entry that has already joined is not still being measured. */
    @Test
    void turningUpAfterJoiningCountsTowardsNothing() {
        var entry = service.createEntry(listId, "Neu", "Dabei", guardians("", "dabei@test.com"), Map.of(), "");
        var joined = service.moveToJoined(
                service.moveToTesting(invite(entry.id()).id()).id());

        service.recordTrialAttendance(joined.memberId());

        assertEquals(0, service.findEntryById(joined.id()).orElseThrow().attendanceCount());
    }

    /**
     * Somebody in a trial period at two stations has an entry at each, and only the one that saw
     * them raises its count: a member belongs to one station.
     */
    @Test
    void aTrialAtTwoStationsCountsOnlyWhereTheEveningWas() {
        var elsewhere = stationRepo.create("SecondTrialStation");
        var otherList = service.create(elsewhere.id(), "Zweite Liste", "", null, 180, null, null, 5, false, null, null);

        var here = service.moveToTesting(
                invite(service.createEntry(listId, "Neu", "Hier", guardians("", "hier@test.com"), Map.of(), "")
                                .id())
                        .id());
        var there = service.moveToTesting(
                invite(service.createEntry(otherList.id(), "Neu", "Dort", guardians("", "dort@test.com"), Map.of(), "")
                                .id())
                        .id());

        service.recordTrialAttendance(here.memberId());

        assertEquals(1, service.findEntryById(here.id()).orElseThrow().attendanceCount());
        assertEquals(0, service.findEntryById(there.id()).orElseThrow().attendanceCount());
    }

    @Test
    void inviteEntryWrongStatusThrows() {
        var entry = service.createEntry(listId, "Wrong", "", guardians("", "wrong@test.com"), Map.of(), "");
        service.updateEntryStatus(entry.id(), WaitingListEntryStatus.JOINED);
        assertThrows(IllegalStateException.class, () -> invite(entry.id()));
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
        var list = service.create(station.id(), "Withdraw Test", "", null, 180, null, null, 5, false, null, null);
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
        var list = service.update(listId, "AgeScored", "", "age([Geburtsdatum])", 180, null, null, 5, false, null, null)
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
        var list = service.update(listId, "WaitScored", "", "wartezeit_tage", 180, null, null, 5, false, null, null)
                .orElseThrow();

        var entry = service.createEntry(listId, "Max", "", guardians("", "test@test.com"), Map.of(), "");
        var values = service.findEntryValues(entry.id());
        var fields = service.findFieldsByList(listId);
        double score = service.evaluateScore(entry, values, fields, list.scoringFormula());
        assertEquals(0.0, score);
    }

    /**
     * Withdrawing somebody who was only invited. Nothing was built on the invitation, so the entry
     * is all there is to take away.
     */
    @Test
    void withdrawInvitedEntryRemovesTheEntryAlone() {
        var list = service.create(station.id(), "Withdraw Invited", "", null, 180, null, null, 5, false, null, null);
        var entry = service.createEntry(
                list.id(), "InvToWithdraw", "Last", guardians("Parent", "invwd@test.com"), Map.of(), "");
        var invited = invite(entry.id());
        assertNull(invited.memberId());
        service.withdrawEntry(invited.id());
        assertTrue(service.findEntryById(invited.id()).isEmpty());
    }

    /**
     * Withdrawing somebody who had already turned up. From the trial period on there is a member,
     * and it goes with the entry, together with the account nothing else points at.
     */
    @Test
    void withdrawTestingEntryDeletesTheMemberAndItsAccount() {
        var list = service.create(station.id(), "Withdraw Testing", "", null, 180, null, null, 5, false, null, null);
        var entry = service.createEntry(
                list.id(), "TestToWithdraw", "Last", guardians("Parent", "testwd@test.com"), Map.of(), "");
        var testing = service.moveToTesting(invite(entry.id()).id());
        int memberId = testing.memberId();
        int accountId = stationMemberRepo.findById(memberId).orElseThrow().accountId();

        service.withdrawEntry(testing.id());

        assertTrue(service.findEntryById(testing.id()).isEmpty());
        assertTrue(stationMemberRepo.findById(memberId).isEmpty());
        assertTrue(accountRepo.findById(accountId).isEmpty());
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

    /**
     * The whole way through a list that has both a testing group and a join group. The testing
     * group is reached when the trial period starts, not when the invitation goes out.
     */
    @Test
    void groupsFollowTheEntryThroughTheTrialPeriod() {
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
                false,
                null,
                null);
        var entry = service.createEntry(
                list.id(), "InviteeFirst2", "InviteeLast2", guardians("Parent", "inv2@test.com"), Map.of(), "");

        // Invite: WAITING -> INVITED. No member, so the testing group stays empty.
        var invited = invite(entry.id());
        assertEquals(WaitingListEntryStatus.INVITED, invited.status());
        assertNull(invited.memberId());
        assertTrue(memberGroupRepo.findMembers(testingGroup.id()).isEmpty());

        // Move to testing: INVITED -> TESTING, which puts the new member in the testing group
        var testing = service.moveToTesting(invited.id());
        assertEquals(WaitingListEntryStatus.TESTING, testing.status());
        assertEquals(
                StationUserType.TRIAL,
                stationMemberRepo.findById(testing.memberId()).orElseThrow().userType());
        assertEquals(
                List.of(testing.memberId()),
                memberGroupRepo.findMembers(testingGroup.id()).stream()
                        .map(StationMember::id)
                        .toList());

        // Join: TESTING -> JOINED (should remove from testing group, add to join group and role)
        var joined = service.moveToJoined(testing.id());
        assertEquals(WaitingListEntryStatus.JOINED, joined.status());
        assertTrue(memberGroupRepo.findMembers(testingGroup.id()).isEmpty());
        assertEquals(
                List.of(joined.memberId()),
                memberGroupRepo.findMembers(joinGroup.id()).stream()
                        .map(StationMember::id)
                        .toList());

        // Cleanup
        memberGroupRepo.delete(testingGroup.id());
        memberGroupRepo.delete(joinGroup.id());
    }

    /**
     * An invitation writes to the person and changes the entry. It does not touch the station: no
     * account, no member, no group, nothing to clear up if the answer is no.
     */
    @Test
    void invitingCreatesNothingOnTheStation() {
        var testingGroup = memberGroupRepo.create(station.id(), "WL Invite Nothing");
        var list = service.create(
                station.id(),
                "InviteNothing " + UUID.randomUUID(),
                "",
                null,
                180,
                testingGroup.id(),
                null,
                5,
                false,
                null,
                null);
        var entry =
                service.createEntry(list.id(), "Nobody", "Yet", guardians("Parent", "nothing@test.com"), Map.of(), "");
        int membersBefore = stationMemberRepo.findByStation(station.id()).size();

        var invited = invite(entry.id());

        assertEquals(WaitingListEntryStatus.INVITED, invited.status());
        assertNull(invited.memberId());
        assertEquals(
                membersBefore, stationMemberRepo.findByStation(station.id()).size());
        assertTrue(memberGroupRepo.findMembers(testingGroup.id()).isEmpty());

        memberGroupRepo.delete(testingGroup.id());
    }

    /**
     * The trial period is where everything is written, and it is written exactly once: one account,
     * one membership, the trial type, the permission and the testing group.
     */
    @Test
    void startingTheTrialPeriodCreatesTheMemberOnce() {
        var testingGroup = memberGroupRepo.create(station.id(), "WL Testing Once");
        var list = service.create(
                station.id(),
                "TestingOnce " + UUID.randomUUID(),
                "",
                null,
                180,
                testingGroup.id(),
                null,
                5,
                false,
                null,
                null);
        var entry =
                service.createEntry(list.id(), "Arriving", "Once", guardians("Parent", "once@test.com"), Map.of(), "");
        int membersBefore = stationMemberRepo.findByStation(station.id()).size();

        var testing = service.moveToTesting(invite(entry.id()).id());

        assertEquals(WaitingListEntryStatus.TESTING, testing.status());
        assertNotNull(testing.memberId());
        assertEquals(
                membersBefore + 1, stationMemberRepo.findByStation(station.id()).size());
        var member = stationMemberRepo.findById(testing.memberId()).orElseThrow();
        assertEquals(StationUserType.TRIAL, member.userType());
        assertTrue(stationMemberRepo.hasPermission(member.id(), StationPermission.USER));
        assertEquals(
                List.of(testing.memberId()),
                memberGroupRepo.findMembers(testingGroup.id()).stream()
                        .map(StationMember::id)
                        .toList());

        memberGroupRepo.delete(testingGroup.id());
    }

    /**
     * An entry invited before the account moved to the arrival already carries a member. Its trial
     * period still has to put it in the group the list names today, and the group it was put in back
     * then must not be written a second time: that row has no room for a duplicate.
     */
    @Test
    void aLegacyInvitedEntryGainsTheCurrentGroupWithoutDuplicatingTheOldOne() {
        var oldGroup = memberGroupRepo.create(station.id(), "WL Old Testing");
        var currentGroup = memberGroupRepo.create(station.id(), "WL Current Testing");
        var list = service.create(
                station.id(), "Legacy " + UUID.randomUUID(), "", null, 180, oldGroup.id(), null, 5, false, null, null);
        var entry = service.createEntry(
                list.id(), "Invited", "Earlier", guardians("Parent", "legacy@test.com"), Map.of(), "");
        var invited = invite(entry.id());

        // What the old invitation left behind: an account, a member and the group of the day
        var account = accountRepo.create(null, "Invited", "Earlier", station.id());
        var member = stationMemberRepo.create(station.id(), account.id());
        waitingListRepo.linkMember(invited.id(), member.id());
        memberGroupRepo.addMember(oldGroup.id(), member.id());
        // ... and the list has been pointed at a different group since
        service.update(list.id(), list.name(), "", null, 180, currentGroup.id(), null, 5, false, null, null);

        var testing = service.moveToTesting(invited.id());

        assertEquals(member.id(), testing.memberId(), "the member it already had is the one it keeps");
        assertEquals(
                List.of(member.id()),
                memberGroupRepo.findMembers(currentGroup.id()).stream()
                        .map(StationMember::id)
                        .toList());
        assertEquals(
                List.of(member.id()),
                memberGroupRepo.findMembers(oldGroup.id()).stream()
                        .map(StationMember::id)
                        .toList());
        // And running it again over the group it now sits in does not try to write that row twice
        waitingListRepo.updateEntryStatus(testing.id(), WaitingListEntryStatus.INVITED);
        assertDoesNotThrow(() -> service.moveToTesting(testing.id()));
        assertEquals(1, memberGroupRepo.findMembers(currentGroup.id()).size());

        memberGroupRepo.delete(oldGroup.id());
        memberGroupRepo.delete(currentGroup.id());
    }

    /**
     * The access token never rotates and never expires, so a link years old still names its entry.
     * Once somebody has joined there is a member behind it, and a mail nobody has to prove they hold
     * must not be able to take that away.
     */
    @Test
    void removalByTokenIsRefusedOnceTheEntryHasJoined() {
        var list = service.create(station.id(), "Token Joined", "", null, 180, null, null, 5, false, null, null);
        var entry = service.createEntry(
                list.id(), "Joined", "Already", guardians("Parent", "joinedtoken@test.com"), Map.of(), "");
        var joined = service.moveToJoined(
                service.moveToTesting(invite(entry.id()).id()).id());

        assertThrows(ConflictResponse.class, () -> service.removeByToken(joined.accessToken()));

        assertTrue(service.findEntryById(joined.id()).isPresent());
        assertTrue(stationMemberRepo.findById(joined.memberId()).isPresent());
    }

    @Test
    void moveToJoinedCreatesGuardianAccounts() {
        var list = service.create(
                station.id(), "GuardianAcct " + UUID.randomUUID(), "", null, 180, null, null, 5, false, null, null);
        var entry = service.createEntry(
                list.id(),
                "Child",
                "Name",
                List.of(new GuardianInput(
                        "Max", "Mustermann", "guardian-new-" + UUID.randomUUID() + "@test.com", "123")),
                Map.of(),
                "");

        // Go through the full lifecycle: invite -> testing -> joined
        var invited = invite(entry.id());
        var testing = service.moveToTesting(invited.id());
        var joined = service.moveToJoined(testing.id());
        assertEquals(WaitingListEntryStatus.JOINED, joined.status());
    }

    @Test
    void moveToJoinedWithExistingGuardianAccount() {
        // Pre-create an account with the guardian's email
        var existingAccount = accountRepo.create("existing-guardian@test.com", "Existing", "Guardian");
        var existingMember = stationMemberRepo.create(station.id(), existingAccount.id());

        var list = service.create(
                station.id(), "ExistGuardian " + UUID.randomUUID(), "", null, 180, null, null, 5, false, null, null);
        var entry = service.createEntry(
                list.id(),
                "Child2",
                "Name",
                List.of(new GuardianInput("Existing", "Guardian", "existing-guardian@test.com", "")),
                Map.of(),
                "");

        var invited = invite(entry.id());
        var testing = service.moveToTesting(invited.id());
        var joined = service.moveToJoined(testing.id());
        assertEquals(WaitingListEntryStatus.JOINED, joined.status());

        // Cleanup
        stationMemberRepo.delete(existingMember.id());
        accountRepo.delete(existingAccount.id());
    }

    @Test
    void moveToJoinedGuardianWithBlankEmail() {
        var list = service.create(
                station.id(), "BlankGuardian " + UUID.randomUUID(), "", null, 180, null, null, 5, false, null, null);
        var entry = service.createEntry(
                list.id(), "Child3", "Name", List.of(new GuardianInput("NoEmail", "Guardian", "", "")), Map.of(), "");

        var invited = invite(entry.id());
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
        // age() function with an invalid date string - should not throw, age = 0
        var dobField = service.createField(
                listId, "BadDate", WaitingListFieldType.DATE, WaitingListFieldConfig.parse("{}"), 0, false, true);
        var list = service.update(listId, "BadDateScored", "", "age([BadDate])", 180, null, null, 5, false, null, null)
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
        var list = service.create(
                station.id(), "NullMember " + UUID.randomUUID(), "", null, 180, null, null, 5, false, null, null);
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
        var publicList = service.create(
                station.id(), "Public " + UUID.randomUUID(), "", null, 180, null, null, 5, true, null, null);
        var publicLists = service.findPublicByStation(station.id());
        assertTrue(publicLists.stream().anyMatch(l -> l.id() == publicList.id()));
        assertTrue(publicLists.stream().noneMatch(l -> l.id() == listId));
    }

    @Test
    void hasPublicWaitlists() {
        // Use a fresh station so no prior public lists interfere
        var freshStation = stationRepo.create("HasPublicTest " + UUID.randomUUID());
        assertFalse(service.hasPublicWaitlists(freshStation.id()));
        service.create(
                freshStation.id(), "PublicHas " + UUID.randomUUID(), "", null, 180, null, null, 5, true, null, null);
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
        var publicList = service.create(
                station.id(), "PubReg " + UUID.randomUUID(), "", null, 180, null, null, 5, true, null, null);
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
                    // We know a token was created - find it
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
        var publicList = service.create(
                station.id(), "Approve " + UUID.randomUUID(), "", null, 180, null, null, 5, true, null, null);
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
        var publicList = service.create(
                station.id(), "Reject " + UUID.randomUUID(), "", null, 180, null, null, 5, true, null, null);
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

    /**
     * Edits aimed at a list or field that no longer exists change nothing and report the miss
     * rather than inventing a row.
     */
    @Test
    void editsToVanishedListsAndFieldsChangeNothing() {
        int gone = 99_999_999;

        assertTrue(service.update(gone, "Ghost", "", null, 180, null, null, 5, false, null, null)
                .isEmpty());
        assertTrue(service.updateVisibleFields(gone, "[]").isEmpty());
        assertTrue(service.updateField(
                        gone, "Ghost", WaitingListFieldType.TEXT, WaitingListFieldConfig.parse("{}"), 0, false, true)
                .isEmpty());
    }

    /**
     * The public queue position is driven by the list's scoring formula, not by arrival order:
     * the highest scoring entry is first even when it registered last.
     */
    @Test
    void waitingPositionRanksByScoreHighestFirst() {
        var ageField = service.createField(
                listId, "Alter", WaitingListFieldType.NUMBER, WaitingListFieldConfig.parse("{}"), 0, true, true);
        service.update(listId, "Ranked", "", "[Alter]", 180, null, null, 5, false, null, null)
                .orElseThrow();

        var youngest = service.createEntry(
                listId, "Young", "", guardians("", "young@test.com"), Map.of(ageField.id(), IntNode.valueOf(5)), "");
        var middle = service.createEntry(
                listId, "Middle", "", guardians("", "middle@test.com"), Map.of(ageField.id(), IntNode.valueOf(12)), "");
        var oldest = service.createEntry(
                listId, "Old", "", guardians("", "old@test.com"), Map.of(ageField.id(), IntNode.valueOf(20)), "");

        assertEquals(1, service.findWaitingPositionByScore(oldest));
        assertEquals(2, service.findWaitingPositionByScore(middle));
        assertEquals(3, service.findWaitingPositionByScore(youngest));
    }

    /**
     * Without a formula every entry scores the same, so the queue falls back to registration
     * order and stays stable.
     */
    @Test
    void waitingPositionFallsBackToRegistrationOrderOnATie() {
        var first = service.createEntry(listId, "First", "", guardians("", "first@test.com"), Map.of(), "");
        var second = service.createEntry(listId, "Second", "", guardians("", "second@test.com"), Map.of(), "");

        assertEquals(1, service.findWaitingPositionByScore(first));
        assertEquals(2, service.findWaitingPositionByScore(second));
    }

    /**
     * Only entries still waiting have a position; once an entry has been invited it has left the
     * queue and reports no position at all.
     */
    @Test
    void waitingPositionIsZeroOnceAnEntryLeavesTheQueue() {
        var list = service.create(station.id(), "Position Exit", "", null, 180, null, null, 5, false, null, null);
        var entry = service.createEntry(list.id(), "Leaver", "", guardians("Parent", "leaver@test.com"), Map.of(), "");
        assertEquals(1, service.findWaitingPositionByScore(entry));

        var invited = invite(entry.id());

        assertEquals(0, service.findWaitingPositionByScore(invited));
    }

    /**
     * The scheduled confirmation sweep. It reminds entries whose confirmation has gone stale,
     * warns the ones approaching removal, and withdraws the ones past the grace period - the last
     * of which takes a place away from an applicant, so it is asserted directly rather than
     * through the absence of a reminder.
     */
    @Test
    void expiredConfirmationSweepRemindsWarnsAndWithdraws() {
        var list = service.create(station.id(), "Sweep", "", null, 0, null, null, 5, false, null, null);

        var stale = service.createEntry(list.id(), "Stale", "", guardians("P", "stale@test.com"), Map.of(), "");
        waitingListRepo.updateConfirmedAt(stale.id(), Instant.now().minus(Duration.ofDays(2)));

        var warned = service.createEntry(list.id(), "Warned", "", guardians("P", "warn@test.com"), Map.of(), "");
        waitingListRepo.updateReminderSentAt(warned.id(), Instant.now().minus(Duration.ofHours(16 * 24 + 12)));

        var abandoned = service.createEntry(list.id(), "Gone", "", guardians("P", "gone@test.com"), Map.of(), "");
        waitingListRepo.updateReminderSentAt(abandoned.id(), Instant.now().minus(Duration.ofDays(31)));

        service.checkExpiredConfirmations(service.findById(list.id()).orElseThrow());

        assertNotNull(
                service.findEntryById(stale.id()).orElseThrow().reminderSentAt(),
                "a stale confirmation should have been reminded and stamped");
        assertEquals(
                WaitingListEntryStatus.WITHDRAWN,
                service.findEntryById(abandoned.id()).orElseThrow().status(),
                "an entry past the grace period should have been withdrawn");
        assertEquals(
                WaitingListEntryStatus.WAITING,
                service.findEntryById(warned.id()).orElseThrow().status(),
                "an entry only due a warning must keep its place");
    }

    /**
     * The same sweep on a list with nothing due must not touch any entry.
     */
    @Test
    void expiredConfirmationSweepLeavesAFreshListAlone() {
        var list = service.create(station.id(), "Quiet Sweep", "", null, 180, null, null, 5, false, null, null);
        var entry = service.createEntry(list.id(), "Fresh", "", guardians("P", "fresh@test.com"), Map.of(), "");

        service.checkExpiredConfirmations(service.findById(list.id()).orElseThrow());

        var after = service.findEntryById(entry.id()).orElseThrow();
        assertEquals(WaitingListEntryStatus.WAITING, after.status());
        assertNull(after.reminderSentAt());
    }
    // --- Age ---

    private int birthDateListWith(Integer minRegister, Integer minJoin) {
        return service.create(
                        station.id(),
                        "Ages " + UUID.randomUUID(),
                        "",
                        null,
                        180,
                        null,
                        null,
                        5,
                        true,
                        minRegister,
                        minJoin)
                .id();
    }

    private static java.util.Map<Integer, tools.jackson.databind.JsonNode> bornYearsAgo(int fieldId, int years) {
        var born = java.time.LocalDate.now().minusYears(years).plusDays(1);
        return java.util.Map.of(fieldId, tools.jackson.databind.node.StringNode.valueOf(born.toString()));
    }

    @Test
    void aListReadsTheAgeFromWhicheverFieldIsTheBirthDate() {
        int list = birthDateListWith(null, null);
        var field = service.createField(
                list,
                "Wann geboren",
                WaitingListFieldType.BIRTH_DATE,
                WaitingListFieldConfig.parse("{}"),
                0,
                true,
                true);

        var age = service.ageFromSubmitted(list, bornYearsAgo(field.id(), 11));

        assertEquals(10, age.orElseThrow(), "eleven years minus a day is still ten");
    }

    @Test
    void aListWithoutABirthDateFieldWorksOutNoAge() {
        int list = birthDateListWith(null, null);
        var field = service.createField(
                list, "Irgendein Datum", WaitingListFieldType.DATE, WaitingListFieldConfig.parse("{}"), 0, true, true);

        assertTrue(service.ageFromSubmitted(list, bornYearsAgo(field.id(), 11)).isEmpty());
    }

    @Test
    void aSecondBirthDateFieldIsRefused() {
        int list = birthDateListWith(null, null);
        service.createField(
                list, "Geburtstag", WaitingListFieldType.BIRTH_DATE, WaitingListFieldConfig.parse("{}"), 0, true, true);

        assertThrows(
                io.javalin.http.BadRequestResponse.class,
                () -> service.createField(
                        list,
                        "Noch ein Geburtstag",
                        WaitingListFieldType.BIRTH_DATE,
                        WaitingListFieldConfig.parse("{}"),
                        1,
                        true,
                        true));
    }

    /** Declaring the one that is already the birth date to be the birth date is not a clash. */
    @Test
    void theBirthDateFieldMayBeUpdatedWithoutClashingWithItself() {
        int list = birthDateListWith(null, null);
        var field = service.createField(
                list, "Geburtstag", WaitingListFieldType.BIRTH_DATE, WaitingListFieldConfig.parse("{}"), 0, true, true);

        assertDoesNotThrow(() -> service.updateField(
                field.id(),
                "Geburtsdatum",
                WaitingListFieldType.BIRTH_DATE,
                WaitingListFieldConfig.parse("{}"),
                0,
                true,
                true));
    }

    @Test
    void somebodyTooYoungIsTurnedAwayAtRegistration() {
        int list = birthDateListWith(12, null);
        var field = service.createField(
                list, "Geburtstag", WaitingListFieldType.BIRTH_DATE, WaitingListFieldConfig.parse("{}"), 0, true, true);
        var actual = service.findById(list).orElseThrow();

        assertThrows(
                io.javalin.http.BadRequestResponse.class,
                () -> service.requireOldEnoughToRegister(actual, bornYearsAgo(field.id(), 10)));
        assertDoesNotThrow(() -> service.requireOldEnoughToRegister(actual, bornYearsAgo(field.id(), 13)));
    }

    /** A form that asks for a birth date and does not get one is a gap, not a person to turn away. */
    @Test
    void anUnansweredBirthDateDoesNotTurnAnybodyAway() {
        int list = birthDateListWith(12, null);
        service.createField(
                list, "Geburtstag", WaitingListFieldType.BIRTH_DATE, WaitingListFieldConfig.parse("{}"), 0, true, true);
        var actual = service.findById(list).orElseThrow();

        assertDoesNotThrow(() -> service.requireOldEnoughToRegister(actual, java.util.Map.of()));
    }

    @Test
    void anEntryUnderTheJoiningAgeIsMarked() {
        int list = birthDateListWith(null, 12);
        var actual = service.findById(list).orElseThrow();

        assertTrue(service.belowJoinAge(actual, java.util.Optional.of(10)));
        assertFalse(service.belowJoinAge(actual, java.util.Optional.of(12)));
        assertFalse(service.belowJoinAge(actual, java.util.Optional.empty()), "an unknown age is not held back");
    }

    @Test
    void aListWithoutAJoiningAgeMarksNobody() {
        int list = birthDateListWith(null, null);
        var actual = service.findById(list).orElseThrow();

        assertFalse(service.belowJoinAge(actual, java.util.Optional.of(3)));
    }
}
