/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.route;

import dev.chojo.ember.api.MemberIdentity;
import dev.chojo.ember.api.RouteHarness;
import dev.chojo.ember.api.TestSessions;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.refusal.EventRefusal;
import dev.chojo.ember.feature.events.entity.CoursePartSummary;
import dev.chojo.ember.feature.events.entity.EventCourse;
import dev.chojo.ember.feature.events.entity.EventCoursePart;
import dev.chojo.ember.feature.events.entity.EventCourseRegistration;
import dev.chojo.ember.feature.events.entity.RegistrationStatus;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.events.service.EventCourseRegistrationService;
import dev.chojo.ember.feature.events.service.EventCourseRegistrationService.CourseStanding;
import dev.chojo.ember.feature.events.service.EventCourseService;
import dev.chojo.ember.feature.members.service.GuardianPolicy;
import dev.chojo.ember.feature.members.service.MemberIdentityFactory;
import dev.chojo.ember.feature.restriction.RestrictionAudience;
import dev.chojo.ember.feature.restriction.RestrictionMode;
import io.javalin.testtools.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static dev.chojo.ember.api.RouteHarness.PREFIX;
import static dev.chojo.ember.api.RouteHarness.body;
import static dev.chojo.ember.api.RouteHarness.json;
import static dev.chojo.ember.api.RouteHarness.refusalOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The course routes over HTTP: who may shape a course, who may sign up whom, and that a course
 * kept from the reader reads as absent.
 */
class EventCourseRoutesTest {
    private static final int STATION_ID = 3;
    private static final int COURSE_ID = 70;
    private static final int CHILD_ID = 12;
    private static final String COURSE = PREFIX + "/events/courses/" + COURSE_ID;

    private EventCourseService courses;
    private EventCourseRegistrationService signUps;
    private GuardianPolicy guardians;
    private EventVisibility visibility;
    private RouteHarness harness;

    @BeforeEach
    void serve() {
        courses = mock(EventCourseService.class);
        signUps = mock(EventCourseRegistrationService.class);
        guardians = mock(GuardianPolicy.class);
        var identities = mock(MemberIdentityFactory.class);
        when(identities.local(anyInt(), anyInt()))
                .thenAnswer(call -> new MemberIdentity(UUID.randomUUID(), UUID.randomUUID()));
        var course = new EventCourse(
                COURSE_ID,
                STATION_ID,
                "Jugendflamme 1",
                null,
                12,
                null,
                false,
                RestrictionMode.AND,
                RestrictionMode.AND,
                false,
                null,
                null);
        when(courses.requireCourse(STATION_ID, COURSE_ID)).thenReturn(course);
        when(courses.findByStation(STATION_ID)).thenReturn(List.of(course));
        when(courses.registrationAudience(COURSE_ID)).thenReturn(RestrictionAudience.empty());
        when(courses.viewAudience(COURSE_ID)).thenReturn(RestrictionAudience.empty());
        when(courses.canSee(eq(COURSE_ID), anyInt(), anySet())).thenReturn(true);
        when(courses.canSeeAny(eq(COURSE_ID), anyList(), anySet())).thenReturn(true);
        when(courses.isMemberHere(eq(STATION_ID), anyInt())).thenReturn(true);
        when(courses.canSignUp(eq(COURSE_ID), anyInt(), anySet())).thenReturn(true);
        when(courses.create(anyInt(), any(), any(), any(), any())).thenReturn(course);
        when(guardians.household(any())).thenReturn(List.of(TestSessions.MEMBER_ID));
        when(signUps.standings(eq(COURSE_ID), anyList())).thenAnswer(call -> {
            List<Integer> members = call.getArgument(1);
            return members.stream()
                    .map(memberId -> new CourseStanding(
                            memberId,
                            new EventCourseRegistration(
                                    1, COURSE_ID, memberId, RegistrationStatus.ACCEPTED, Instant.now(), null),
                            Set.of(101, 102),
                            List.of()))
                    .toList();
        });
        visibility = mock(EventVisibility.class);
        harness = RouteHarness.serving(new EventCourseRoutes(courses, signUps, guardians, identities, visibility));
    }

    private Response call(String method, String path, String json, StationPermission... permissions) {
        var session = harness.as(TestSessions.member(STATION_ID, permissions));
        return harness.request(client -> switch (method) {
            case "GET" -> client.get(path, session);
            case "POST" -> client.post(path, body(json), session);
            case "PUT" -> client.put(path, body(json), session);
            default -> client.delete(path, json == null ? null : body(json), session);
        });
    }

    @Test
    void aMemberListsAndOpensTheCoursesMeantForThem() {
        var listed = call("GET", PREFIX + "/events/courses", null, StationPermission.USER);
        assertEquals(200, listed.code());
        assertEquals("Jugendflamme 1", json(listed).get(0).get("name").asString());

        var opened = call("GET", COURSE, null, StationPermission.USER);
        assertEquals(200, opened.code());
        assertEquals(2, json(opened).get("household").get(0).get("parts").size());
    }

    @Test
    void aCourseKeptFromTheReaderReadsAsAbsent() {
        when(courses.canSeeAny(eq(COURSE_ID), anyList(), anySet())).thenReturn(false);

        assertEquals(
                0,
                json(call("GET", PREFIX + "/events/courses", null, StationPermission.USER))
                        .size());
        assertEquals(EventRefusal.COURSE_NOT_HERE, refusalOf(call("GET", COURSE, null, StationPermission.USER)));
    }

    @Test
    void onlyWhoeverEditsAppointmentsShapesACourse() {
        String create = """
                {"details": {"name": "Jugendflamme 1", "requiresConfirmation": false},
                 "sets": [{"name": "Übung", "minimum": 2}]}""";
        assertEquals(
                403,
                call("POST", PREFIX + "/events/courses", create, StationPermission.USER)
                        .code());
        assertEquals(
                201,
                call("POST", PREFIX + "/events/courses", create, StationPermission.EVENT_EDIT)
                        .code());

        when(courses.addPart(STATION_ID, COURSE_ID, 101, 5)).thenReturn(new EventCoursePart(101, 5, COURSE_ID));
        assertEquals(
                403,
                call("POST", COURSE + "/parts", "{\"eventId\": 101, \"setId\": 5}", StationPermission.USER)
                        .code());
        assertEquals(
                201,
                call("POST", COURSE + "/parts", "{\"eventId\": 101, \"setId\": 5}", StationPermission.EVENT_EDIT)
                        .code());
    }

    @Test
    void aMemberSignsUpThemselvesWithinTheRules() {
        var response = call("POST", COURSE + "/sign-up", "{\"parts\": [101, 102]}", StationPermission.USER);

        assertEquals(201, response.code());
        verify(signUps).register(STATION_ID, COURSE_ID, TestSessions.MEMBER_ID, Set.of(101, 102), null, false);
    }

    @Test
    void aMemberNotTakenByTheCourseIsRefused() {
        when(courses.canSignUp(eq(COURSE_ID), anyInt(), anySet())).thenReturn(false);

        var response = call("POST", COURSE + "/sign-up", "{\"parts\": [101]}", StationPermission.USER);

        assertEquals(EventRefusal.MEMBER_NOT_INVITED_TO_EVENT, refusalOf(response));
        verify(signUps, never()).register(anyInt(), anyInt(), anyInt(), anySet(), any(), eq(false));
    }

    @Test
    void aGuardianSignsUpTheirChildWithinTheRulesAndAStrangerIsRefused() {
        when(guardians.mayActFor(any(), eq(CHILD_ID))).thenReturn(true);
        String forChild = "{\"memberId\": %d, \"parts\": [101]}".formatted(CHILD_ID);

        assertEquals(
                201,
                call("POST", COURSE + "/sign-up", forChild, StationPermission.USER)
                        .code());
        verify(signUps).register(STATION_ID, COURSE_ID, CHILD_ID, Set.of(101), TestSessions.MEMBER_ID, false);

        String forStranger = "{\"memberId\": 99, \"parts\": [101]}";
        assertEquals(
                EventRefusal.MEMBER_NOT_YOURS_TO_REGISTER,
                refusalOf(call("POST", COURSE + "/sign-up", forStranger, StationPermission.USER)));
    }

    @Test
    void whoeverRunsEventsSigningSomebodyElseUpPassesTheRules() {
        when(courses.canSignUp(eq(COURSE_ID), anyInt(), anySet())).thenReturn(false);
        String forOther = "{\"memberId\": 99, \"parts\": [101]}";

        var response =
                call("POST", COURSE + "/sign-up", forOther, StationPermission.USER, StationPermission.EVENT_MANAGER);

        assertEquals(201, response.code());
        verify(signUps).register(STATION_ID, COURSE_ID, 99, Set.of(101), TestSessions.MEMBER_ID, true);
    }

    @Test
    void partsAreChangedAndASignUpWithdrawn() {
        assertEquals(
                200,
                call("PUT", COURSE + "/sign-up", "{\"parts\": [102]}", StationPermission.USER)
                        .code());
        verify(signUps).changeParts(STATION_ID, COURSE_ID, TestSessions.MEMBER_ID, Set.of(102), null, false);

        assertEquals(
                200,
                call("DELETE", COURSE + "/sign-up", null, StationPermission.USER)
                        .code());
        verify(signUps).withdraw(STATION_ID, COURSE_ID, TestSessions.MEMBER_ID);
    }

    @Test
    void onlyWhoeverHandlesRegistrationsSeesAndDecidesSignUps() {
        when(signUps.findByCourse(COURSE_ID))
                .thenReturn(List.of(new EventCourseRegistration(
                        1, COURSE_ID, CHILD_ID, RegistrationStatus.PENDING, Instant.now(), null)));

        assertEquals(
                403,
                call("GET", COURSE + "/sign-ups", null, StationPermission.USER).code());
        assertEquals(
                1,
                json(call("GET", COURSE + "/sign-ups", null, StationPermission.EVENT_REGISTRATION))
                        .size());

        String accept = "{\"status\": \"ACCEPTED\"}";
        assertEquals(
                403,
                call("PUT", COURSE + "/sign-ups/" + CHILD_ID + "/status", accept, StationPermission.USER)
                        .code());
        assertEquals(
                200,
                call("PUT", COURSE + "/sign-ups/" + CHILD_ID + "/status", accept, StationPermission.EVENT_REGISTRATION)
                        .code());
        verify(signUps).decide(STATION_ID, COURSE_ID, CHILD_ID, RegistrationStatus.ACCEPTED);
        verify(signUps, never()).decide(anyInt(), anyInt(), anyInt(), isNull());
    }

    @Test
    void aGuardianOpensTheCourseMeantForTheirChildAndSeesOnlyTheChildsStanding() {
        when(guardians.household(any())).thenReturn(List.of(TestSessions.MEMBER_ID, CHILD_ID));
        when(courses.canSee(eq(COURSE_ID), anyInt(), anySet())).thenReturn(false);
        when(courses.canSee(eq(COURSE_ID), eq(CHILD_ID), anySet())).thenReturn(true);
        when(courses.canSeeAny(eq(COURSE_ID), anyList(), anySet())).thenReturn(true);
        when(courses.canSignUp(eq(COURSE_ID), anyInt(), anySet())).thenReturn(false);
        when(courses.canSignUp(eq(COURSE_ID), eq(CHILD_ID), anySet())).thenReturn(true);

        var opened = json(call("GET", COURSE, null, StationPermission.USER));

        assertEquals(1, opened.get("household").size());
        assertEquals(true, opened.get("household").get(0).get("mayRegister").asBoolean());
        assertEquals(
                1,
                json(call("GET", PREFIX + "/events/courses", null, StationPermission.USER))
                        .size());
    }

    @Test
    void whoeverEditsAppointmentsOrHandlesRegistrationsSeesEveryCourse() {
        when(courses.canSeeAny(eq(COURSE_ID), anyList(), anySet())).thenReturn(false);

        assertEquals(EventRefusal.COURSE_NOT_HERE, refusalOf(call("GET", COURSE, null, StationPermission.USER)));
        assertEquals(
                200,
                call("GET", COURSE, null, StationPermission.USER, StationPermission.EVENT_EDIT)
                        .code());
        assertEquals(
                200,
                call("GET", COURSE, null, StationPermission.USER, StationPermission.EVENT_REGISTRATION)
                        .code());
    }

    @Test
    void somebodyOfAnotherStationCannotBeSignedUp() {
        when(courses.isMemberHere(STATION_ID, 99)).thenReturn(false);
        String forOther = "{\"memberId\": 99, \"parts\": [101]}";

        var response =
                call("POST", COURSE + "/sign-up", forOther, StationPermission.USER, StationPermission.EVENT_MANAGER);

        assertEquals(EventRefusal.COURSE_MEMBER_NOT_HERE, refusalOf(response));
    }

    @Test
    void aSignUpIsWithdrawnEvenWhereTheCourseIsNoLongerMeantForTheReader() {
        when(courses.canSeeAny(eq(COURSE_ID), anyList(), anySet())).thenReturn(false);

        assertEquals(
                200,
                call("DELETE", COURSE + "/sign-up", null, StationPermission.USER)
                        .code());
        verify(signUps).withdraw(STATION_ID, COURSE_ID, TestSessions.MEMBER_ID);
    }

    @Test
    void aMemberTakenOutOfTheAudienceCannotChangeTheirParts() {
        when(courses.canSignUp(eq(COURSE_ID), anyInt(), anySet())).thenReturn(false);

        var response = call("PUT", COURSE + "/sign-up", "{\"parts\": [102]}", StationPermission.USER);

        assertEquals(EventRefusal.MEMBER_NOT_INVITED_TO_EVENT, refusalOf(response));
    }

    @Test
    void anAppointmentSaysWhichCourseItIsAPartOf() {
        var event = mock(StationEvent.class);
        when(event.id()).thenReturn(101);
        when(visibility.requireVisibleEvent(any(), eq(101))).thenReturn(event);
        when(visibility.requireVisibleEvent(any(), eq(102))).thenReturn(event);
        when(courses.partSummary(STATION_ID, 101))
                .thenReturn(new CoursePartSummary(COURSE_ID, "Jugendflamme 1", 5, "Übung", 2, 4));

        var part = call("GET", PREFIX + "/events/101/course", null, StationPermission.USER);
        assertEquals(2, json(part).get("number").asInt());
        when(courses.partSummary(STATION_ID, 101)).thenReturn(null);
        assertEquals(
                204,
                call("GET", PREFIX + "/events/101/course", null, StationPermission.USER)
                        .code());
    }
}
