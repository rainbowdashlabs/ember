/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.service;

import dev.chojo.ember.api.refusal.EventRefusal;
import dev.chojo.ember.api.refusal.Refusal;
import dev.chojo.ember.api.refusal.RefusalResponse;
import dev.chojo.ember.feature.events.entity.AppointmentField;
import dev.chojo.ember.feature.events.entity.EventFieldConfig;
import dev.chojo.ember.feature.events.entity.EventFieldDraft;
import dev.chojo.ember.feature.events.entity.EventGroup;
import dev.chojo.ember.feature.events.entity.EventGroupContent;
import dev.chojo.ember.feature.events.entity.EventGroupDetails;
import dev.chojo.ember.feature.events.entity.EventGroupSetDraft;
import dev.chojo.ember.feature.events.entity.EventQuestionSettings;
import dev.chojo.ember.feature.events.entity.SelfRegistration;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.events.repository.EventGroupRepository;
import dev.chojo.ember.feature.events.repository.EventRegistrationFieldRepository;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.question.FieldType;
import dev.chojo.ember.feature.question.QuestionValues;
import dev.chojo.ember.feature.restriction.RestrictionAudience;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A member field of an event group, such as the team looking after a Jugendflamme weekend: members put
 * themselves in for the whole group or for one appointment, skip one appointment, and every appointment
 * names, and gives a place to, whoever stands in it there.
 */
class EventGroupMemberFieldsTest extends RepositoryTestBase {
    private static Station station;
    private static StationMember anna;
    private static StationMember ben;
    private static final List<Integer> accounts = new ArrayList<>();

    private final EventGroupRepository eventGroupRepo = new EventGroupRepository();
    private EventGroupService groups;
    private EventFieldService fields;

    @BeforeAll
    static void setup() {
        station = stationRepo.create("Group member field Station");
        var annaAccount = accountRepo.create("anna@groupfields.test", "Anna", "Fields");
        var benAccount = accountRepo.create("ben@groupfields.test", "Ben", "Fields");
        accounts.add(annaAccount.id());
        accounts.add(benAccount.id());
        anna = stationMemberRepo.create(station.id(), annaAccount.id());
        ben = stationMemberRepo.create(station.id(), benAccount.id());
    }

    @AfterAll
    static void cleanup() {
        stationRepo.delete(station.id());
        accounts.forEach(accountRepo::delete);
    }

    @BeforeEach
    void wire() {
        groups = new EventGroupService(
                eventGroupRepo,
                eventRepo,
                restrictionService,
                occurrenceCalendar,
                new EventGroupQuestionService(new EventRegistrationFieldRepository(), memberEligibility),
                eventFieldRepo);
        fields = new EventFieldService(
                eventFieldRepo,
                stationMemberRepo,
                memberEligibility,
                eventRepo,
                attendanceRepo,
                eventFieldRegistrationService);
    }

    private static EventFieldDraft memberField(FieldType type, String value) {
        var config = new EventFieldConfig(null, null, null, null, true, null, false);
        return new EventFieldDraft(null, "Betreuer", type, config.settings(), value, false, null, false);
    }

    private EventGroup groupWith(EventFieldDraft field, Instant deadline) {
        return groups.create(
                station.id(),
                new EventGroupDetails("Jugendflamme", null, null, deadline, false, null),
                List.of(new EventGroupSetDraft(null, "Tage", 1)),
                RestrictionAudience.empty(),
                RestrictionAudience.empty(),
                new EventGroupContent(null, List.of(field)));
    }

    private EventGroup groupWith(EventFieldDraft field) {
        return groupWith(field, Instant.now().plus(Duration.ofDays(5)));
    }

    private StationEvent partOf(EventGroup group, int daysAhead) {
        var start = LocalDate.now().plusDays(daysAhead).atTime(9, 0).toInstant(ZoneOffset.UTC);
        var part = eventRepo.create(
                station.id(),
                "Tag " + daysAhead,
                null,
                StationEvent.EventType.ONE_TIME,
                null,
                start,
                start.plus(Duration.ofHours(6)),
                null,
                false,
                null,
                false,
                null,
                null,
                null,
                null,
                null);
        groups.addPart(
                station.id(),
                group.id(),
                part.id(),
                groups.findSets(group.id()).getFirst().id());
        fields.reconcilePart(part.id());
        return part;
    }

    private int groupFieldId(EventGroup group) {
        return groups.findFields(group.id()).getFirst().id();
    }

    private List<Integer> namedOn(StationEvent part) {
        return fields.findByEvent(part.id()).stream()
                .filter(field -> field.name().equals("Betreuer"))
                .map(AppointmentField::value)
                .map(QuestionValues::memberIds)
                .findFirst()
                .orElseThrow();
    }

    private int placesOn(StationEvent part) {
        return eventRegistrationRepo.countAccepted(part.id(), LocalDate.ofInstant(part.startTime(), ZoneOffset.UTC));
    }

    private static void assertRefused(Refusal refusal, Runnable call) {
        var thrown = assertThrows(RefusalResponse.class, call::run);
        assertEquals(refusal.code(), thrown.refusal().code());
    }

    @Test
    void somebodyEnteredForTheWholeGroupStandsOnEveryAppointmentWithAPlace() {
        var group = groupWith(memberField(FieldType.MEMBER_LIST, ""));
        var saturday = partOf(group, 20);
        var sunday = partOf(group, 21);

        var shown = fields.toggleGroupSelfRegistration(group.id(), groupFieldId(group), anna.id(), false);

        assertEquals(List.of(anna.id()), QuestionValues.memberIds(shown.value()));
        assertEquals(List.of(anna.id()), namedOn(saturday));
        assertEquals(List.of(anna.id()), namedOn(sunday));
        assertEquals(1, placesOn(saturday));
        assertEquals(1, placesOn(sunday));
    }

    @Test
    void somebodyEnteredForTheWholeGroupSkipsOneAppointmentAndComesBack() {
        var group = groupWith(memberField(FieldType.MEMBER_LIST, ""));
        var saturday = partOf(group, 22);
        var sunday = partOf(group, 23);
        int fieldId = groupFieldId(group);
        fields.toggleGroupSelfRegistration(group.id(), fieldId, anna.id(), false);

        var skipped = fields.toggleSelfRegistration(saturday.id(), -fieldId, anna.id(), null, false);

        assertTrue(QuestionValues.memberIds(skipped.value()).isEmpty());
        assertEquals(List.of(anna.id()), namedOn(sunday));
        assertEquals(0, placesOn(saturday));
        assertEquals(1, placesOn(sunday));

        fields.toggleSelfRegistration(saturday.id(), -fieldId, anna.id(), null, false);
        assertEquals(List.of(anna.id()), namedOn(saturday));
        assertEquals(1, placesOn(saturday));
    }

    @Test
    void somebodyEnteredForOneAppointmentStandsOnThatOneOnly() {
        var group = groupWith(memberField(FieldType.MEMBER_LIST, ""));
        var saturday = partOf(group, 24);
        var sunday = partOf(group, 25);
        int fieldId = groupFieldId(group);
        fields.toggleGroupSelfRegistration(group.id(), fieldId, anna.id(), false);

        fields.toggleSelfRegistration(saturday.id(), -fieldId, ben.id(), null, false);

        assertEquals(List.of(anna.id(), ben.id()), namedOn(saturday));
        assertEquals(List.of(anna.id()), namedOn(sunday));
        assertEquals(2, placesOn(saturday));

        fields.toggleSelfRegistration(saturday.id(), -fieldId, ben.id(), null, false);
        assertEquals(List.of(anna.id()), namedOn(saturday));
    }

    @Test
    void leavingTheWholeGroupLeavesEveryAppointmentWhateverWasSkipped() {
        var group = groupWith(memberField(FieldType.MEMBER_LIST, ""));
        var saturday = partOf(group, 26);
        var sunday = partOf(group, 27);
        int fieldId = groupFieldId(group);
        fields.toggleGroupSelfRegistration(group.id(), fieldId, anna.id(), false);
        fields.toggleSelfRegistration(saturday.id(), -fieldId, anna.id(), null, false);

        fields.toggleGroupSelfRegistration(group.id(), fieldId, anna.id(), false);

        assertTrue(namedOn(saturday).isEmpty());
        assertTrue(namedOn(sunday).isEmpty());
        assertTrue(
                eventFieldRepo.findEntries(List.of(saturday.id(), sunday.id())).isEmpty());
        assertEquals(0, placesOn(sunday));
    }

    @Test
    void aSingleSlotHeldForTheWholeGroupIsTakenOnAnAppointmentOnlyOnceItIsSkipped() {
        var group = groupWith(memberField(FieldType.MEMBER, ""));
        var saturday = partOf(group, 28);
        var sunday = partOf(group, 29);
        int fieldId = groupFieldId(group);
        fields.toggleGroupSelfRegistration(group.id(), fieldId, anna.id(), false);

        assertRefused(
                EventRefusal.APPOINTMENT_FIELD_SLOT_TAKEN,
                () -> fields.toggleSelfRegistration(saturday.id(), -fieldId, ben.id(), null, false));
        assertRefused(
                EventRefusal.APPOINTMENT_FIELD_SLOT_TAKEN,
                () -> fields.toggleGroupSelfRegistration(group.id(), fieldId, ben.id(), false));

        fields.toggleSelfRegistration(saturday.id(), -fieldId, anna.id(), null, false);
        fields.toggleSelfRegistration(saturday.id(), -fieldId, ben.id(), null, false);

        assertEquals(List.of(ben.id()), namedOn(saturday));
        assertEquals(List.of(anna.id()), namedOn(sunday));
    }

    @Test
    void aClosedGroupTakesNobodyNewButLetsEverybodyLeave() {
        var group =
                groupWith(memberField(FieldType.MEMBER_LIST, ""), Instant.now().minus(1, ChronoUnit.HOURS));
        var saturday = partOf(group, 30);
        int fieldId = groupFieldId(group);

        assertRefused(
                EventRefusal.REGISTRATION_CLOSED_ON_SELF_REGISTRATION,
                () -> fields.toggleGroupSelfRegistration(group.id(), fieldId, anna.id(), false));
        fields.toggleGroupSelfRegistration(group.id(), fieldId, anna.id(), true);
        assertEquals(List.of(anna.id()), namedOn(saturday));

        fields.toggleGroupSelfRegistration(group.id(), fieldId, anna.id(), false);
        assertTrue(namedOn(saturday).isEmpty());
    }

    @Test
    void aFieldOfAnotherGroupOrAnAppointmentOutsideTheGroupIsNotHere() {
        var group = groupWith(memberField(FieldType.MEMBER_LIST, ""));
        var other = groupWith(memberField(FieldType.MEMBER_LIST, ""));
        var foreignPart = partOf(other, 31);
        int fieldId = groupFieldId(group);

        assertRefused(
                EventRefusal.APPOINTMENT_FIELD_NOT_HERE_FOR_SELF_REGISTRATION,
                () -> fields.toggleGroupSelfRegistration(other.id(), fieldId, anna.id(), false));
        assertRefused(
                EventRefusal.APPOINTMENT_FIELD_NOT_HERE_FOR_SELF_REGISTRATION,
                () -> fields.toggleSelfRegistration(foreignPart.id(), -fieldId, anna.id(), null, false));
    }

    @Test
    void savingTheGroupKeepsTheEntriesOfAFieldItStillNamesAndDropsTheRest() {
        var group = groupWith(memberField(FieldType.MEMBER_LIST, ""));
        var saturday = partOf(group, 32);
        int fieldId = groupFieldId(group);
        fields.toggleSelfRegistration(saturday.id(), -fieldId, ben.id(), null, false);
        var stored = groups.findFields(group.id()).getFirst();

        eventFieldRepo.replaceGroupFields(
                group.id(),
                List.of(new EventFieldDraft(
                        fieldId, "Betreuer", stored.fieldType(), stored.config(), stored.value(), false, null, false)));
        assertEquals(fieldId, groupFieldId(group));
        assertEquals(List.of(ben.id()), namedOn(saturday));

        eventFieldRepo.replaceGroupFields(group.id(), List.of());
        assertTrue(eventFieldRepo.findEntries(List.of(saturday.id())).isEmpty());
    }

    @Test
    void aGroupMemberFieldIsNeverAnsweredPerDateAndNamesOnlyMembersItIsNarrowedTo() {
        var perDate = new EventFieldDraft(
                null,
                "Betreuer",
                FieldType.MEMBER_LIST,
                new EventFieldConfig(null, null, null, null, true, null, true).settings(),
                "",
                false,
                null,
                false);

        assertRefused(EventRefusal.APPOINTMENT_FIELD_TYPE_NOT_OFFERED, () -> groupWith(perDate));
        var named = groupWith(memberField(FieldType.MEMBER_LIST, QuestionValues.formatMembers(List.of(anna.id()))));
        var part = partOf(named, 33);
        assertEquals(List.of(anna.id()), namedOn(part));
        assertEquals(1, placesOn(part));
    }

    @Test
    void theDailyPassReachesTheAppointmentsOfAGroupWithAMemberField() {
        var group = groupWith(memberField(FieldType.MEMBER_LIST, ""));
        var part = partOf(group, 34);

        assertTrue(eventFieldRepo.findEventIdsWithMemberFields().contains(part.id()));
    }

    @Test
    void aPartLeavingTheGroupGivesBackThePlacesItsFieldHandedOut() {
        var group = groupWith(memberField(FieldType.MEMBER_LIST, ""));
        var saturday = partOf(group, 35);
        fields.toggleGroupSelfRegistration(group.id(), groupFieldId(group), anna.id(), false);
        assertEquals(1, placesOn(saturday));

        groups.removePart(station.id(), group.id(), saturday.id());
        fields.reconcilePart(saturday.id());

        assertEquals(0, placesOn(saturday));
    }

    @Test
    void somebodyOutsideTheFieldsNarrowingCannotEnterTheWholeGroup() {
        var team = memberGroupRepo.create(station.id(), "Jugendwarte");
        memberGroupRepo.addMember(team.id(), ben.id());
        var narrowed = new EventFieldDraft(
                null,
                "Betreuer",
                FieldType.MEMBER_LIST_OF_GROUP,
                new EventFieldConfig(null, team.id(), null, null, true, null, false).settings(),
                "",
                false,
                null,
                false);
        var group = groupWith(narrowed);
        int fieldId = groupFieldId(group);

        assertRefused(
                EventRefusal.APPOINTMENT_FIELD_NOT_OPEN_TO_YOU,
                () -> fields.toggleGroupSelfRegistration(group.id(), fieldId, anna.id(), false));
        var stored = groups.findFields(group.id()).getFirst();
        assertEquals(
                SelfRegistration.NOT_ELIGIBLE,
                fields.asSeenOnGroup(group.id(), stored, anna.id(), false).selfRegistrationState());
        assertEquals(
                SelfRegistration.OPEN,
                fields.asSeenOnGroup(group.id(), stored, ben.id(), false).selfRegistrationState());
    }

    @Test
    void aClosedGroupSaysSoToEverybodyButWhoeverRunsIt() {
        var group =
                groupWith(memberField(FieldType.MEMBER_LIST, ""), Instant.now().minus(1, ChronoUnit.HOURS));
        var stored = groups.findFields(group.id()).getFirst();

        assertEquals(
                SelfRegistration.CLOSED,
                fields.asSeenOnGroup(group.id(), stored, anna.id(), false).selfRegistrationState());
        assertEquals(
                SelfRegistration.OPEN,
                fields.asSeenOnGroup(group.id(), stored, anna.id(), true).selfRegistrationState());
    }

    @Test
    void anAppointmentTellsItsReaderWhetherTheyStandInTheFieldForTheWholeGroup() {
        var group = groupWith(memberField(FieldType.MEMBER_LIST, ""));
        var saturday = partOf(group, 36);
        int fieldId = groupFieldId(group);
        fields.toggleGroupSelfRegistration(group.id(), fieldId, anna.id(), false);
        fields.toggleSelfRegistration(saturday.id(), -fieldId, ben.id(), null, false);
        var inherited = fields.findByEvent(saturday.id()).stream()
                .filter(field -> field.id() == -fieldId)
                .findFirst()
                .orElseThrow();

        assertTrue(fields.asSeenBy(inherited, anna.id(), false).readerInWholeGroup());
        assertFalse(fields.asSeenBy(inherited, ben.id(), false).readerInWholeGroup());
        assertTrue(loadedFor(saturday, fieldId, anna).readerInWholeGroup());
        assertFalse(loadedFor(saturday, fieldId, ben).readerInWholeGroup());

        fields.toggleSelfRegistration(saturday.id(), -fieldId, anna.id(), null, false);
        assertTrue(loadedFor(saturday, fieldId, anna).readerInWholeGroup());
    }

    private AppointmentField loadedFor(StationEvent part, int groupFieldId, StationMember reader) {
        return fields.findByEventFor(part.id(), null, reader.id(), false).stream()
                .filter(field -> field.id() == -groupFieldId)
                .findFirst()
                .orElseThrow();
    }

    @Test
    void anAppointmentReplacingTheFieldTakesNoEntriesInIt() {
        var group = groupWith(memberField(FieldType.MEMBER_LIST, ""));
        var saturday = partOf(group, 37);
        eventFieldRepo.create(
                saturday.id(),
                "betreuer",
                FieldType.TEXT,
                EventQuestionSettings.empty(),
                "Leitung",
                0,
                false,
                null,
                false);

        assertRefused(
                EventRefusal.APPOINTMENT_FIELD_NOT_HERE_FOR_SELF_REGISTRATION,
                () -> fields.toggleSelfRegistration(saturday.id(), -groupFieldId(group), anna.id(), null, false));
    }

    @Test
    void aFieldChangingItsKindLosesItsEntries() {
        var group = groupWith(memberField(FieldType.MEMBER_LIST, ""));
        var saturday = partOf(group, 38);
        int fieldId = groupFieldId(group);
        fields.toggleSelfRegistration(saturday.id(), -fieldId, ben.id(), null, false);
        var stored = groups.findFields(group.id()).getFirst();

        eventFieldRepo.replaceGroupFields(
                group.id(),
                List.of(new EventFieldDraft(
                        fieldId, "Betreuer", FieldType.MEMBER, stored.config(), "", false, null, false)));

        assertTrue(eventFieldRepo.findEntries(List.of(saturday.id())).isEmpty());
    }
}
