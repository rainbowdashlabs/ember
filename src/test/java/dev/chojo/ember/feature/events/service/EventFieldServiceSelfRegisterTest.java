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
import dev.chojo.ember.feature.events.entity.MemberFieldValue;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.members.service.UserTagService;
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
                eventFieldRepo, stationMemberRepo, memberGroupRepo, tagService, eventRepo, attendanceRepo);

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
        return new EventFieldConfig(null, null, null, null, true, null);
    }

    @Test
    void singleMemberTakeAndReleaseSlot() {
        var field = createField(EventFieldType.MEMBER, selfRegConfig());

        var afterTake = service.toggleSelfRegistration(eventId, field.id(), memberA);
        assertEquals(String.valueOf(memberA), afterTake.value());

        var afterRelease = service.toggleSelfRegistration(eventId, field.id(), memberA);
        assertEquals("", afterRelease.value());
    }

    @Test
    void singleMemberConflictWhenSlotHeldByOther() {
        var field = createField(EventFieldType.MEMBER, selfRegConfig());
        service.toggleSelfRegistration(eventId, field.id(), memberA);

        assertThrows(ConflictResponse.class, () -> service.toggleSelfRegistration(eventId, field.id(), memberB));
    }

    @Test
    void listMemberAddRemove() {
        var field = createField(EventFieldType.MEMBER_LIST, selfRegConfig());

        service.toggleSelfRegistration(eventId, field.id(), memberA);
        service.toggleSelfRegistration(eventId, field.id(), memberB);
        var afterBoth = eventFieldRepo.findById(field.id()).orElseThrow();
        assertEquals(List.of(memberA, memberB), MemberFieldValue.parseIds(afterBoth.value()));

        service.toggleSelfRegistration(eventId, field.id(), memberA);
        var afterAGone = eventFieldRepo.findById(field.id()).orElseThrow();
        assertEquals(List.of(memberB), MemberFieldValue.parseIds(afterAGone.value()));
    }

    @Test
    void groupConstraintHonored() {
        var config = new EventFieldConfig(null, groupId, null, null, true, null);
        var field = createField(EventFieldType.MEMBER_OF_GROUP, config);

        var afterA = service.toggleSelfRegistration(eventId, field.id(), memberA);
        assertEquals(String.valueOf(memberA), afterA.value());

        service.toggleSelfRegistration(eventId, field.id(), memberA);
        assertThrows(ForbiddenResponse.class, () -> service.toggleSelfRegistration(eventId, field.id(), memberB));
    }

    @Test
    void groupConstraintMissingGroupRejected() {
        var field = createField(EventFieldType.MEMBER_OF_GROUP, selfRegConfig());
        assertThrows(BadRequestResponse.class, () -> service.toggleSelfRegistration(eventId, field.id(), memberA));
    }

    @Test
    void userTypeConstraintHonored() {
        var config = new EventFieldConfig(null, null, StationUserType.TEAM, null, true, null);
        var field = createField(EventFieldType.MEMBER_OF_TYPE, config);

        service.toggleSelfRegistration(eventId, field.id(), memberA);
        service.toggleSelfRegistration(eventId, field.id(), memberA);

        assertThrows(ForbiddenResponse.class, () -> service.toggleSelfRegistration(eventId, field.id(), memberB));
    }

    @Test
    void userTypeConstraintMissingRejected() {
        var field = createField(EventFieldType.MEMBER_OF_TYPE, selfRegConfig());
        assertThrows(BadRequestResponse.class, () -> service.toggleSelfRegistration(eventId, field.id(), memberA));
    }

    @Test
    void tagConstraintHonored() {
        var config = new EventFieldConfig(null, null, null, tagId, true, null);
        var field = createField(EventFieldType.MEMBER_OF_TAG, config);

        service.toggleSelfRegistration(eventId, field.id(), memberA);
        service.toggleSelfRegistration(eventId, field.id(), memberA);

        assertThrows(ForbiddenResponse.class, () -> service.toggleSelfRegistration(eventId, field.id(), memberB));
    }

    @Test
    void tagConstraintMissingRejected() {
        var field = createField(EventFieldType.MEMBER_OF_TAG, selfRegConfig());
        assertThrows(BadRequestResponse.class, () -> service.toggleSelfRegistration(eventId, field.id(), memberA));
    }

    @Test
    void listOfGroupTagAndTypeWork() {
        var listOfGroup = createField(
                EventFieldType.MEMBER_LIST_OF_GROUP, new EventFieldConfig(null, groupId, null, null, true, null));
        service.toggleSelfRegistration(eventId, listOfGroup.id(), memberA);
        assertEquals(
                List.of(memberA),
                MemberFieldValue.parseIds(
                        eventFieldRepo.findById(listOfGroup.id()).orElseThrow().value()));

        var listOfType = createField(
                EventFieldType.MEMBER_LIST_OF_TYPE,
                new EventFieldConfig(null, null, StationUserType.TEAM, null, true, null));
        service.toggleSelfRegistration(eventId, listOfType.id(), memberA);
        assertEquals(
                List.of(memberA),
                MemberFieldValue.parseIds(
                        eventFieldRepo.findById(listOfType.id()).orElseThrow().value()));

        var listOfTag = createField(
                EventFieldType.MEMBER_LIST_OF_TAG, new EventFieldConfig(null, null, null, tagId, true, null));
        service.toggleSelfRegistration(eventId, listOfTag.id(), memberA);
        assertEquals(
                List.of(memberA),
                MemberFieldValue.parseIds(
                        eventFieldRepo.findById(listOfTag.id()).orElseThrow().value()));
    }

    @Test
    void selfRegistrationDisabledIsRejected() {
        var field = createField(EventFieldType.MEMBER, EventFieldConfig.parse("{}"));
        assertThrows(BadRequestResponse.class, () -> service.toggleSelfRegistration(eventId, field.id(), memberA));
    }

    @Test
    void nonMemberFieldIsRejected() {
        var field = createField(EventFieldType.STRING, selfRegConfig());
        assertThrows(BadRequestResponse.class, () -> service.toggleSelfRegistration(eventId, field.id(), memberA));
    }

    @Test
    void wrongEventIdIsNotFound() {
        var field = createField(EventFieldType.MEMBER, selfRegConfig());
        assertThrows(
                NotFoundResponse.class, () -> service.toggleSelfRegistration(eventId + 99999, field.id(), memberA));
    }

    @Test
    void missingFieldIsNotFound() {
        assertThrows(NotFoundResponse.class, () -> service.toggleSelfRegistration(eventId, 987654, memberA));
    }

    @Test
    void missingMemberIsBadRequest() {
        var field = createField(EventFieldType.MEMBER, selfRegConfig());
        assertThrows(BadRequestResponse.class, () -> service.toggleSelfRegistration(eventId, field.id(), 987654));
    }
}
