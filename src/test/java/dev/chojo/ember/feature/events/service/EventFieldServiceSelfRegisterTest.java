/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.service;

import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.feature.events.entity.EventField;
import dev.chojo.ember.feature.events.entity.EventFieldConfig;
import dev.chojo.ember.feature.events.entity.EventFieldType;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.members.service.UserTagService;
import dev.chojo.ember.feature.question.QuestionValues;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.ConflictResponse;
import io.javalin.http.ForbiddenResponse;
import io.javalin.http.NotFoundResponse;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class EventFieldServiceSelfRegisterTest extends RepositoryTestBase {

    private static EventFieldService service;
    private static int eventId;
    private static int memberA;
    private static int memberB;
    private static int groupId;
    private static int tagId;

    @BeforeAll
    static void setup() {
        UserTagService tagService = new UserTagService(userTagRepo, memberGroupRepo);
        service = new EventFieldService(
                eventFieldRepo,
                stationMemberRepo,
                memberGroupRepo,
                tagService,
                eventRepo,
                attendanceRepo,
                eventFieldRegistrationService);

        Station station = stationRepo.create("SelfReg Station");

        var accA = accountRepo.create("a@selfreg.test", "Alice", "Anders");
        var accB = accountRepo.create("b@selfreg.test", "Bob", "Brown");
        var mA = stationMemberRepo.create(station.id(), accA.id());
        var mB = stationMemberRepo.create(station.id(), accB.id());
        memberA = mA.id();
        memberB = mB.id();
        stationMemberRepo.setUserType(memberA, StationUserType.TEAM);
        stationMemberRepo.setUserType(memberB, StationUserType.MEMBER);

        var group = memberGroupRepo.create(station.id(), "Drivers");
        groupId = group.id();
        memberGroupRepo.addMember(groupId, memberA);

        var tag = userTagRepo.create(station.id(), "OnCall");
        tagId = tag.id();
        userTagRepo.addMember(tagId, memberA);

        Instant start = Instant.now().plus(1, ChronoUnit.DAYS);
        var event = eventRepo.create(
                station.id(),
                "SelfReg Event",
                "desc",
                StationEvent.EventType.ONE_TIME,
                null,
                start,
                start.plus(2, ChronoUnit.HOURS),
                null,
                false,
                null,
                false,
                null,
                null,
                null,
                null,
                null);
        eventId = event.id();
    }

    private EventField createField(EventFieldType type, EventFieldConfig config) {
        return eventFieldRepo.create(eventId, "F" + type.name(), type, config, "", 0, false, null, false);
    }

    private EventFieldConfig selfRegConfig() {
        return new EventFieldConfig(null, null, null, null, true, null, false);
    }

    @Test
    void singleMemberTakeAndReleaseSlot() {
        var field = createField(EventFieldType.MEMBER, selfRegConfig());

        var afterTake = service.toggleSelfRegistration(eventId, field.id(), memberA, null);
        assertEquals(String.valueOf(memberA), afterTake.value());

        var afterRelease = service.toggleSelfRegistration(eventId, field.id(), memberA, null);
        assertEquals("", afterRelease.value());
    }

    @Test
    void singleMemberConflictWhenSlotHeldByOther() {
        var field = createField(EventFieldType.MEMBER, selfRegConfig());
        service.toggleSelfRegistration(eventId, field.id(), memberA, null);

        assertThrows(ConflictResponse.class, () -> service.toggleSelfRegistration(eventId, field.id(), memberB, null));
    }

    @Test
    void listMemberAddRemove() {
        var field = createField(EventFieldType.MEMBER_LIST, selfRegConfig());

        service.toggleSelfRegistration(eventId, field.id(), memberA, null);
        service.toggleSelfRegistration(eventId, field.id(), memberB, null);
        var afterBoth = eventFieldRepo.findById(field.id()).orElseThrow();
        assertEquals(List.of(memberA, memberB), QuestionValues.memberIds(afterBoth.value()));

        service.toggleSelfRegistration(eventId, field.id(), memberA, null);
        var afterAGone = eventFieldRepo.findById(field.id()).orElseThrow();
        assertEquals(List.of(memberB), QuestionValues.memberIds(afterAGone.value()));
    }

    @Test
    void groupConstraintHonored() {
        var config = new EventFieldConfig(null, groupId, null, null, true, null, false);
        var field = createField(EventFieldType.MEMBER_OF_GROUP, config);

        var afterA = service.toggleSelfRegistration(eventId, field.id(), memberA, null);
        assertEquals(String.valueOf(memberA), afterA.value());

        service.toggleSelfRegistration(eventId, field.id(), memberA, null);
        assertThrows(ForbiddenResponse.class, () -> service.toggleSelfRegistration(eventId, field.id(), memberB, null));
    }

    @Test
    void groupConstraintMissingGroupRejected() {
        var field = createField(EventFieldType.MEMBER_OF_GROUP, selfRegConfig());
        assertThrows(
                BadRequestResponse.class, () -> service.toggleSelfRegistration(eventId, field.id(), memberA, null));
    }

    @Test
    void userTypeConstraintHonored() {
        var config = new EventFieldConfig(null, null, StationUserType.TEAM, null, true, null, false);
        var field = createField(EventFieldType.MEMBER_OF_TYPE, config);

        service.toggleSelfRegistration(eventId, field.id(), memberA, null);
        service.toggleSelfRegistration(eventId, field.id(), memberA, null);

        assertThrows(ForbiddenResponse.class, () -> service.toggleSelfRegistration(eventId, field.id(), memberB, null));
    }

    @Test
    void userTypeConstraintMissingRejected() {
        var field = createField(EventFieldType.MEMBER_OF_TYPE, selfRegConfig());
        assertThrows(
                BadRequestResponse.class, () -> service.toggleSelfRegistration(eventId, field.id(), memberA, null));
    }

    @Test
    void tagConstraintHonored() {
        var config = new EventFieldConfig(null, null, null, tagId, true, null, false);
        var field = createField(EventFieldType.MEMBER_OF_TAG, config);

        service.toggleSelfRegistration(eventId, field.id(), memberA, null);
        service.toggleSelfRegistration(eventId, field.id(), memberA, null);

        assertThrows(ForbiddenResponse.class, () -> service.toggleSelfRegistration(eventId, field.id(), memberB, null));
    }

    @Test
    void tagConstraintMissingRejected() {
        var field = createField(EventFieldType.MEMBER_OF_TAG, selfRegConfig());
        assertThrows(
                BadRequestResponse.class, () -> service.toggleSelfRegistration(eventId, field.id(), memberA, null));
    }

    @Test
    void listOfGroupTagAndTypeWork() {
        var listOfGroup = createField(
                EventFieldType.MEMBER_LIST_OF_GROUP,
                new EventFieldConfig(null, groupId, null, null, true, null, false));
        service.toggleSelfRegistration(eventId, listOfGroup.id(), memberA, null);
        assertEquals(
                List.of(memberA),
                QuestionValues.memberIds(
                        eventFieldRepo.findById(listOfGroup.id()).orElseThrow().value()));

        var listOfType = createField(
                EventFieldType.MEMBER_LIST_OF_TYPE,
                new EventFieldConfig(null, null, StationUserType.TEAM, null, true, null, false));
        service.toggleSelfRegistration(eventId, listOfType.id(), memberA, null);
        assertEquals(
                List.of(memberA),
                QuestionValues.memberIds(
                        eventFieldRepo.findById(listOfType.id()).orElseThrow().value()));

        var listOfTag = createField(
                EventFieldType.MEMBER_LIST_OF_TAG, new EventFieldConfig(null, null, null, tagId, true, null, false));
        service.toggleSelfRegistration(eventId, listOfTag.id(), memberA, null);
        assertEquals(
                List.of(memberA),
                QuestionValues.memberIds(
                        eventFieldRepo.findById(listOfTag.id()).orElseThrow().value()));
    }

    @Test
    void selfRegistrationDisabledIsRejected() {
        var field = createField(EventFieldType.MEMBER, EventFieldConfig.parse("{}"));
        assertThrows(
                BadRequestResponse.class, () -> service.toggleSelfRegistration(eventId, field.id(), memberA, null));
    }

    @Test
    void nonMemberFieldIsRejected() {
        var field = createField(EventFieldType.STRING, selfRegConfig());
        assertThrows(
                BadRequestResponse.class, () -> service.toggleSelfRegistration(eventId, field.id(), memberA, null));
    }

    @Test
    void wrongEventIdIsNotFound() {
        var field = createField(EventFieldType.MEMBER, selfRegConfig());
        assertThrows(
                NotFoundResponse.class,
                () -> service.toggleSelfRegistration(eventId + 99999, field.id(), memberA, null));
    }

    @Test
    void missingFieldIsNotFound() {
        assertThrows(NotFoundResponse.class, () -> service.toggleSelfRegistration(eventId, 987654, memberA, null));
    }

    @Test
    void missingMemberIsBadRequest() {
        var field = createField(EventFieldType.MEMBER, selfRegConfig());
        assertThrows(BadRequestResponse.class, () -> service.toggleSelfRegistration(eventId, field.id(), 987654, null));
    }

    @Test
    void memberFieldIsShownByName() {
        var field = valuedField(EventFieldType.MEMBER, QuestionValues.formatMember(memberA));
        assertEquals("Alice Anders", service.displayValue(field));
    }

    @Test
    void memberListFieldNamesEveryoneInOrder() {
        var field = valuedField(EventFieldType.MEMBER_LIST, QuestionValues.formatMembers(List.of(memberB, memberA)));
        assertEquals("Bob Brown, Alice Anders", service.displayValue(field));
    }

    @Test
    void memberThatNoLongerExistsKeepsItsNumber() {
        var field = valuedField(EventFieldType.MEMBER, "987654");
        assertEquals("#987654", service.displayValue(field));
    }

    @Test
    void emptyMemberFieldShowsNothing() {
        var field = valuedField(EventFieldType.MEMBER, "");
        assertEquals("", service.displayValue(field));
    }

    @Test
    void plainFieldKeepsItsAnswer() {
        var field = valuedField(EventFieldType.STRING, " Marktplatz ");
        assertEquals("Marktplatz", service.displayValue(field));
    }

    private EventField valuedField(EventFieldType type, String value) {
        return eventFieldRepo.create(
                eventId, "V" + type.name(), type, EventFieldConfig.parse("{}"), value, 0, false, null, false);
    }
}
