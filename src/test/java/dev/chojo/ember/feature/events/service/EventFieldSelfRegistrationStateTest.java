/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.service;

import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.api.refusal.RefusalResponse;
import dev.chojo.ember.feature.events.entity.AppointmentField;
import dev.chojo.ember.feature.events.entity.EventFieldConfig;
import dev.chojo.ember.feature.events.entity.SelfRegistration;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.members.entity.TagVisibility;
import dev.chojo.ember.feature.question.FieldType;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The state a reader is told about a self-registration question, measured against what the toggle
 * then does with the same reader: a question said to be open must be accepted, one said not to be
 * must be refused.
 */
class EventFieldSelfRegistrationStateTest extends RepositoryTestBase {

    private static EventFieldService service;
    private static int stationId;
    private static int inside;
    private static int outside;
    private static int groupId;
    private static int privateTagId;

    @BeforeAll
    static void setup() {
        service = new EventFieldService(
                eventFieldRepo,
                stationMemberRepo,
                memberEligibility,
                eventRepo,
                attendanceRepo,
                eventFieldRegistrationService);
        stationId = stationRepo.create("SelfReg State Station").id();
        inside = stationMemberRepo
                .create(
                        stationId,
                        accountRepo.create("in@selfstate.test", "Ina", "Inside").id())
                .id();
        outside = stationMemberRepo
                .create(
                        stationId,
                        accountRepo
                                .create("out@selfstate.test", "Otto", "Outside")
                                .id())
                .id();
        stationMemberRepo.setUserType(inside, StationUserType.TEAM);
        stationMemberRepo.setUserType(outside, StationUserType.MEMBER);
        groupId = memberGroupRepo.create(stationId, "State Drivers").id();
        memberGroupRepo.addMember(groupId, inside);
        privateTagId = userTagRepo
                .create(stationId, "State Secret", null, TagVisibility.PRIVATE)
                .id();
        userTagRepo.addMember(privateTagId, inside);
    }

    @Test
    void aMemberInsideTheGroupIsToldItIsOpenAndIsTakenIn() {
        int eventId = event(null);
        var field = field(eventId, FieldType.MEMBER_OF_GROUP, config(groupId, null, null));

        assertEquals(SelfRegistration.OPEN, stateFor(eventId, field, inside, false));
        assertDoesNotThrow(() -> service.toggleSelfRegistration(eventId, field.id(), inside, null, false));
    }

    @Test
    void aMemberOutsideTheGroupIsToldItIsNotForThemAndIsRefused() {
        int eventId = event(null);
        var field = field(eventId, FieldType.MEMBER_OF_GROUP, config(groupId, null, null));

        assertEquals(SelfRegistration.NOT_ELIGIBLE, stateFor(eventId, field, outside, false));
        assertThrows(
                RefusalResponse.class, () -> service.toggleSelfRegistration(eventId, field.id(), outside, null, false));
    }

    @Test
    void aMemberOfAnotherTypeIsToldItIsNotForThem() {
        int eventId = event(null);
        var field = field(eventId, FieldType.MEMBER_OF_TYPE, config(null, StationUserType.TEAM, null));

        assertEquals(SelfRegistration.OPEN, stateFor(eventId, field, inside, false));
        assertEquals(SelfRegistration.NOT_ELIGIBLE, stateFor(eventId, field, outside, false));
    }

    @Test
    void aPrivateTagAdmitsNobodyEvenItsBearer() {
        int eventId = event(null);
        var field = field(eventId, FieldType.MEMBER_OF_TAG, config(null, null, privateTagId));

        assertEquals(SelfRegistration.NOT_ELIGIBLE, stateFor(eventId, field, inside, false));
        assertThrows(
                RefusalResponse.class, () -> service.toggleSelfRegistration(eventId, field.id(), inside, null, false));
    }

    @Test
    void aClosedAppointmentIsClosedToMembersButNotToWhoeverRunsIt() {
        int eventId = event(Instant.now().minus(1, ChronoUnit.DAYS));
        var field = field(eventId, FieldType.MEMBER_LIST, config(null, null, null));

        assertEquals(SelfRegistration.CLOSED, stateFor(eventId, field, inside, false));
        assertThrows(
                RefusalResponse.class, () -> service.toggleSelfRegistration(eventId, field.id(), inside, null, false));
        assertEquals(SelfRegistration.OPEN, stateFor(eventId, field, inside, true));
    }

    @Test
    void beingOutsideOutweighsBeingClosed() {
        int eventId = event(Instant.now().minus(1, ChronoUnit.DAYS));
        var field = field(eventId, FieldType.MEMBER_OF_GROUP, config(groupId, null, null));

        assertEquals(SelfRegistration.NOT_ELIGIBLE, stateFor(eventId, field, outside, false));
    }

    @Test
    void aMemberWhoLeftTheGroupStepsOutAndIsThenToldItIsNotForThem() {
        int eventId = event(null);
        var field = field(eventId, FieldType.MEMBER_OF_GROUP, config(groupId, null, null));
        int leaver = stationMemberRepo
                .create(
                        stationId,
                        accountRepo
                                .create("leaver@selfstate.test", "Lea", "Leaver")
                                .id())
                .id();
        memberGroupRepo.addMember(groupId, leaver);
        service.toggleSelfRegistration(eventId, field.id(), leaver, null, false);
        memberGroupRepo.removeMember(groupId, leaver);

        var afterStepOut = service.toggleSelfRegistration(eventId, field.id(), leaver, null, false);

        assertEquals(
                SelfRegistration.NOT_ELIGIBLE,
                service.asSeenBy(afterStepOut, leaver, false).selfRegistrationState());
    }

    @Test
    void aQuestionAnsweredPerDateIsMeasuredForTheDateAskedAbout() {
        int eventId = event(null);
        var field = field(
                eventId, FieldType.MEMBER_OF_GROUP, new EventFieldConfig(null, groupId, null, null, true, null, true));
        LocalDate day = LocalDate.now().plusDays(3);

        var asked = service.findByEventFor(eventId, day, outside, false).stream()
                .filter(f -> f.id() == field.id())
                .findFirst()
                .orElseThrow();

        assertEquals(SelfRegistration.NOT_ELIGIBLE, asked.selfRegistrationState());
    }

    @Test
    void aQuestionWithoutSelfRegistrationCarriesNoState() {
        int eventId = event(null);
        var field =
                field(eventId, FieldType.MEMBER_LIST, new EventFieldConfig(null, null, null, null, false, null, false));

        assertNull(stateFor(eventId, field, inside, false));
    }

    private static @Nullable SelfRegistration stateFor(
            int eventId, AppointmentField field, int memberId, boolean runsTheEvent) {
        return service.findByEventFor(eventId, null, memberId, runsTheEvent).stream()
                .filter(f -> f.id() == field.id())
                .findFirst()
                .orElseThrow()
                .selfRegistrationState();
    }

    private static int event(@Nullable Instant deadline) {
        Instant start = Instant.now().plus(3, ChronoUnit.DAYS);
        return eventRepo
                .create(
                        stationId,
                        "State Event",
                        null,
                        StationEvent.EventType.ONE_TIME,
                        null,
                        start,
                        start.plus(2, ChronoUnit.HOURS),
                        null,
                        deadline != null,
                        deadline,
                        false,
                        null,
                        null,
                        null,
                        null,
                        null)
                .id();
    }

    private static AppointmentField field(int eventId, FieldType type, EventFieldConfig config) {
        return eventFieldRepo.create(eventId, "Q" + type.name(), type, config.settings(), "", 0, false, null, false);
    }

    private static EventFieldConfig config(
            @Nullable Integer group, @Nullable StationUserType type, @Nullable Integer tag) {
        return new EventFieldConfig(null, group, type, tag, true, null, false);
    }
}
