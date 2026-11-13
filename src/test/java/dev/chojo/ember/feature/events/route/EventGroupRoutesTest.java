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
import dev.chojo.ember.feature.events.entity.EventGroup;
import dev.chojo.ember.feature.events.entity.EventGroupPart;
import dev.chojo.ember.feature.events.entity.EventGroupPartSummary;
import dev.chojo.ember.feature.events.entity.EventGroupRegistration;
import dev.chojo.ember.feature.events.entity.EventQuestionSettings;
import dev.chojo.ember.feature.events.entity.EventRegistrationField;
import dev.chojo.ember.feature.events.entity.RegistrationFieldValue;
import dev.chojo.ember.feature.events.entity.RegistrationStatus;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.events.service.EventGroupCancellationService;
import dev.chojo.ember.feature.events.service.EventGroupPartFactory;
import dev.chojo.ember.feature.events.service.EventGroupQuestionService;
import dev.chojo.ember.feature.events.service.EventGroupRegistrationService;
import dev.chojo.ember.feature.events.service.EventGroupRegistrationService.EventGroupStanding;
import dev.chojo.ember.feature.events.service.EventGroupService;
import dev.chojo.ember.feature.members.service.GuardianPolicy;
import dev.chojo.ember.feature.members.service.MemberIdentityFactory;
import dev.chojo.ember.feature.question.FieldType;
import dev.chojo.ember.feature.restriction.RestrictionAudience;
import dev.chojo.ember.feature.restriction.RestrictionMode;
import io.javalin.testtools.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
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
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The event group routes over HTTP: who may shape an event group, who may sign up whom, and that an event group
 * kept from the reader reads as absent.
 */
class EventGroupRoutesTest {
    private static final int STATION_ID = 3;
    private static final int EVENT_GROUP_ID = 70;
    private static final int CHILD_ID = 12;
    private static final String EVENT_GROUP = PREFIX + "/events/groups/" + EVENT_GROUP_ID;

    private EventGroupService eventGroups;
    private EventGroupRegistrationService signUps;
    private EventGroupQuestionService questions;
    private GuardianPolicy guardians;
    private EventVisibility visibility;
    private EventGroupCancellationService cancellation;
    private EventGroupPartFactory partFactory;
    private RouteHarness harness;

    @BeforeEach
    void serve() {
        eventGroups = mock(EventGroupService.class);
        signUps = mock(EventGroupRegistrationService.class);
        guardians = mock(GuardianPolicy.class);
        var identities = mock(MemberIdentityFactory.class);
        when(identities.local(anyInt(), anyInt()))
                .thenAnswer(call -> new MemberIdentity(UUID.randomUUID(), UUID.randomUUID()));
        var eventGroup = new EventGroup(
                EVENT_GROUP_ID,
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
                null,
                null);
        when(eventGroups.requireEventGroup(STATION_ID, EVENT_GROUP_ID)).thenReturn(eventGroup);
        when(eventGroups.findByStation(STATION_ID)).thenReturn(List.of(eventGroup));
        when(eventGroups.registrationAudience(EVENT_GROUP_ID)).thenReturn(RestrictionAudience.empty());
        when(eventGroups.viewAudience(EVENT_GROUP_ID)).thenReturn(RestrictionAudience.empty());
        when(eventGroups.canSee(eq(EVENT_GROUP_ID), anyInt(), anySet())).thenReturn(true);
        when(eventGroups.canSeeAny(eq(EVENT_GROUP_ID), anyList(), anySet())).thenReturn(true);
        when(eventGroups.isMemberHere(eq(STATION_ID), anyInt())).thenReturn(true);
        when(eventGroups.canSignUp(eq(EVENT_GROUP_ID), anyInt(), anySet())).thenReturn(true);
        when(eventGroups.createWith(anyInt(), any(), any(), any(), any(), any(), any()))
                .thenReturn(eventGroup);
        when(guardians.household(any())).thenReturn(List.of(TestSessions.MEMBER_ID));
        when(signUps.standings(eq(EVENT_GROUP_ID), anyList())).thenAnswer(call -> {
            List<Integer> members = call.getArgument(1);
            return members.stream()
                    .map(memberId -> new EventGroupStanding(
                            memberId,
                            new EventGroupRegistration(
                                    1, EVENT_GROUP_ID, memberId, RegistrationStatus.ACCEPTED, Instant.now(), null),
                            Set.of(101, 102),
                            List.of()))
                    .toList();
        });
        visibility = mock(EventVisibility.class);
        cancellation = mock(EventGroupCancellationService.class);
        partFactory = mock(EventGroupPartFactory.class);
        questions = mock(EventGroupQuestionService.class);
        harness = RouteHarness.serving(new EventGroupRoutes(
                eventGroups, signUps, guardians, identities, visibility, cancellation, partFactory, questions));
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
    void aMemberListsAndOpensTheEventGroupsMeantForThem() {
        var listed = call("GET", PREFIX + "/events/groups", null, StationPermission.USER);
        assertEquals(200, listed.code());
        assertEquals("Jugendflamme 1", json(listed).get(0).get("name").asString());

        var opened = call("GET", EVENT_GROUP, null, StationPermission.USER);
        assertEquals(200, opened.code());
        assertEquals(2, json(opened).get("household").get(0).get("parts").size());
    }

    @Test
    void anEventGroupKeptFromTheReaderReadsAsAbsent() {
        when(eventGroups.canSeeAny(eq(EVENT_GROUP_ID), anyList(), anySet())).thenReturn(false);

        assertEquals(
                0,
                json(call("GET", PREFIX + "/events/groups", null, StationPermission.USER))
                        .size());
        assertEquals(
                EventRefusal.EVENT_GROUP_NOT_HERE, refusalOf(call("GET", EVENT_GROUP, null, StationPermission.USER)));
    }

    @Test
    void onlyWhoeverEditsAppointmentsShapesAnEventGroup() {
        String create = """
                {"details": {"name": "Jugendflamme 1", "requiresConfirmation": false},
                 "sets": [{"name": "Übung", "minimum": 2}]}""";
        assertEquals(
                403,
                call("POST", PREFIX + "/events/groups", create, StationPermission.USER)
                        .code());
        assertEquals(
                201,
                call("POST", PREFIX + "/events/groups", create, StationPermission.EVENT_EDIT)
                        .code());

        when(eventGroups.addPart(STATION_ID, EVENT_GROUP_ID, 101, 5))
                .thenReturn(new EventGroupPart(101, 5, EVENT_GROUP_ID));
        assertEquals(
                403,
                call("POST", EVENT_GROUP + "/parts", "{\"eventId\": 101, \"setId\": 5}", StationPermission.USER)
                        .code());
        assertEquals(
                201,
                call("POST", EVENT_GROUP + "/parts", "{\"eventId\": 101, \"setId\": 5}", StationPermission.EVENT_EDIT)
                        .code());
    }

    @Test
    void aMemberSignsUpThemselvesWithinTheRules() {
        var response = call("POST", EVENT_GROUP + "/sign-up", "{\"parts\": [101, 102]}", StationPermission.USER);

        assertEquals(201, response.code());
        verify(signUps)
                .register(STATION_ID, EVENT_GROUP_ID, TestSessions.MEMBER_ID, Set.of(101, 102), Map.of(), null, false);
    }

    @Test
    void aMemberNotTakenByTheEventGroupIsRefused() {
        when(eventGroups.canSignUp(eq(EVENT_GROUP_ID), anyInt(), anySet())).thenReturn(false);

        var response = call("POST", EVENT_GROUP + "/sign-up", "{\"parts\": [101]}", StationPermission.USER);

        assertEquals(EventRefusal.MEMBER_NOT_INVITED_TO_EVENT, refusalOf(response));
        verify(signUps, never()).register(anyInt(), anyInt(), anyInt(), anySet(), anyMap(), any(), eq(false));
    }

    @Test
    void aGuardianSignsUpTheirChildWithinTheRulesAndAStrangerIsRefused() {
        when(guardians.mayActFor(any(), eq(CHILD_ID))).thenReturn(true);
        String forChild = "{\"memberId\": %d, \"parts\": [101]}".formatted(CHILD_ID);

        assertEquals(
                201,
                call("POST", EVENT_GROUP + "/sign-up", forChild, StationPermission.USER)
                        .code());
        verify(signUps)
                .register(STATION_ID, EVENT_GROUP_ID, CHILD_ID, Set.of(101), Map.of(), TestSessions.MEMBER_ID, false);

        String forStranger = "{\"memberId\": 99, \"parts\": [101]}";
        assertEquals(
                EventRefusal.MEMBER_NOT_YOURS_TO_REGISTER,
                refusalOf(call("POST", EVENT_GROUP + "/sign-up", forStranger, StationPermission.USER)));
    }

    @Test
    void whoeverRunsEventsSigningSomebodyElseUpPassesTheRules() {
        when(eventGroups.canSignUp(eq(EVENT_GROUP_ID), anyInt(), anySet())).thenReturn(false);
        String forOther = "{\"memberId\": 99, \"parts\": [101]}";

        var response = call(
                "POST", EVENT_GROUP + "/sign-up", forOther, StationPermission.USER, StationPermission.EVENT_MANAGER);

        assertEquals(201, response.code());
        verify(signUps).register(STATION_ID, EVENT_GROUP_ID, 99, Set.of(101), Map.of(), TestSessions.MEMBER_ID, true);
    }

    @Test
    void partsAreChangedAndASignUpWithdrawn() {
        assertEquals(
                200,
                call("PUT", EVENT_GROUP + "/sign-up", "{\"parts\": [102]}", StationPermission.USER)
                        .code());
        verify(signUps).changeParts(STATION_ID, EVENT_GROUP_ID, TestSessions.MEMBER_ID, Set.of(102), null, false);

        assertEquals(
                200,
                call("DELETE", EVENT_GROUP + "/sign-up", null, StationPermission.USER)
                        .code());
        verify(signUps).withdraw(STATION_ID, EVENT_GROUP_ID, TestSessions.MEMBER_ID);
    }

    @Test
    void onlyWhoeverHandlesRegistrationsSeesAndDecidesSignUps() {
        when(signUps.findByEventGroup(EVENT_GROUP_ID))
                .thenReturn(List.of(new EventGroupRegistration(
                        1, EVENT_GROUP_ID, CHILD_ID, RegistrationStatus.PENDING, Instant.now(), null)));

        assertEquals(
                403,
                call("GET", EVENT_GROUP + "/sign-ups", null, StationPermission.USER)
                        .code());
        assertEquals(
                1,
                json(call("GET", EVENT_GROUP + "/sign-ups", null, StationPermission.EVENT_REGISTRATION))
                        .size());

        String accept = "{\"status\": \"ACCEPTED\"}";
        assertEquals(
                403,
                call("PUT", EVENT_GROUP + "/sign-ups/" + CHILD_ID + "/status", accept, StationPermission.USER)
                        .code());
        assertEquals(
                200,
                call(
                                "PUT",
                                EVENT_GROUP + "/sign-ups/" + CHILD_ID + "/status",
                                accept,
                                StationPermission.EVENT_REGISTRATION)
                        .code());
        verify(signUps).decide(STATION_ID, EVENT_GROUP_ID, CHILD_ID, RegistrationStatus.ACCEPTED);
        verify(signUps, never()).decide(anyInt(), anyInt(), anyInt(), isNull());
    }

    @Test
    void aGuardianOpensTheEventGroupMeantForTheirChildAndSeesOnlyTheChildsStanding() {
        when(guardians.household(any())).thenReturn(List.of(TestSessions.MEMBER_ID, CHILD_ID));
        when(eventGroups.canSee(eq(EVENT_GROUP_ID), anyInt(), anySet())).thenReturn(false);
        when(eventGroups.canSee(eq(EVENT_GROUP_ID), eq(CHILD_ID), anySet())).thenReturn(true);
        when(eventGroups.canSeeAny(eq(EVENT_GROUP_ID), anyList(), anySet())).thenReturn(true);
        when(eventGroups.canSignUp(eq(EVENT_GROUP_ID), anyInt(), anySet())).thenReturn(false);
        when(eventGroups.canSignUp(eq(EVENT_GROUP_ID), eq(CHILD_ID), anySet())).thenReturn(true);

        var opened = json(call("GET", EVENT_GROUP, null, StationPermission.USER));

        assertEquals(1, opened.get("household").size());
        assertEquals(true, opened.get("household").get(0).get("mayRegister").asBoolean());
        assertEquals(
                1,
                json(call("GET", PREFIX + "/events/groups", null, StationPermission.USER))
                        .size());
    }

    private void askAllergiesForOrganisersAndARequiredShirt() {
        var forOrganisers =
                new EventQuestionSettings(null, null, null, null, null, false, false, false, null, null, null, true);
        var required =
                new EventQuestionSettings(null, null, null, null, null, false, false, true, null, null, null, false);
        when(questions.findByEventGroup(EVENT_GROUP_ID))
                .thenReturn(List.of(
                        new EventRegistrationField(
                                5, null, EVENT_GROUP_ID, "Allergien", FieldType.TEXT, forOrganisers, 0, false),
                        new EventRegistrationField(
                                6, null, EVENT_GROUP_ID, "T-Shirt", FieldType.TEXT, required, 1, false)));
        when(questions.answersBySignUp(anyList()))
                .thenReturn(Map.of(1, List.of(new RegistrationFieldValue(1, 5, "Nüsse"))));
        when(signUps.findByEventGroup(EVENT_GROUP_ID))
                .thenReturn(List.of(new EventGroupRegistration(
                        1, EVENT_GROUP_ID, CHILD_ID, RegistrationStatus.ACCEPTED, Instant.now(), null)));
    }

    @Test
    void anAnswerKeptForOrganisersReachesWhoeverEditsAppointmentsButNotWhoeverOnlyHandlesRegistrations() {
        askAllergiesForOrganisersAndARequiredShirt();

        var handled = json(call("GET", EVENT_GROUP + "/sign-ups", null, StationPermission.EVENT_REGISTRATION));
        assertEquals(0, handled.get(0).get("answers").size());
        assertEquals(true, handled.get(0).get("answersMissing").asBoolean());

        var decided = json(call(
                "PUT",
                EVENT_GROUP + "/sign-ups/" + CHILD_ID + "/status",
                "{\"status\": \"ACCEPTED\"}",
                StationPermission.EVENT_REGISTRATION));
        assertEquals(0, decided.get("answers").size());

        var edited = json(call(
                "GET",
                EVENT_GROUP + "/sign-ups",
                null,
                StationPermission.EVENT_REGISTRATION,
                StationPermission.EVENT_EDIT));
        assertEquals("Nüsse", edited.get(0).get("answers").get(0).get("value").asString());
    }

    @Test
    void aGuardianReadsTheAnswersKeptForOrganisersOfTheirChild() {
        askAllergiesForOrganisersAndARequiredShirt();
        when(guardians.household(any())).thenReturn(List.of(CHILD_ID));
        when(guardians.mayActFor(any(), eq(CHILD_ID))).thenReturn(true);

        var opened = json(call("GET", EVENT_GROUP, null, StationPermission.USER));

        assertEquals(
                "Nüsse",
                opened.get("household")
                        .get(0)
                        .get("answers")
                        .get(0)
                        .get("value")
                        .asString());
        assertEquals(2, opened.get("questions").size());
    }

    @Test
    void answersAreChangedByTheMemberAndPastTheDeadlineByWhoeverRunsEventsForSomebodyElse() {
        String own = "{\"answers\": [{\"fieldId\": 6, \"value\": \"M\"}]}";
        assertEquals(
                200,
                call("PUT", EVENT_GROUP + "/sign-up/answers", own, StationPermission.USER)
                        .code());
        verify(signUps).changeAnswers(STATION_ID, EVENT_GROUP_ID, TestSessions.MEMBER_ID, Map.of(6, "M"), true, false);

        String forOther = "{\"memberId\": 99, \"answers\": [{\"fieldId\": 6, \"value\": \"L\"}]}";
        assertEquals(
                200,
                call(
                                "PUT",
                                EVENT_GROUP + "/sign-up/answers",
                                forOther,
                                StationPermission.USER,
                                StationPermission.EVENT_MANAGER)
                        .code());
        verify(signUps).changeAnswers(STATION_ID, EVENT_GROUP_ID, 99, Map.of(6, "L"), false, true);
    }

    @Test
    void whoeverEditsAppointmentsOrHandlesRegistrationsSeesEveryEventGroup() {
        when(eventGroups.canSeeAny(eq(EVENT_GROUP_ID), anyList(), anySet())).thenReturn(false);

        assertEquals(
                EventRefusal.EVENT_GROUP_NOT_HERE, refusalOf(call("GET", EVENT_GROUP, null, StationPermission.USER)));
        assertEquals(
                200,
                call("GET", EVENT_GROUP, null, StationPermission.USER, StationPermission.EVENT_EDIT)
                        .code());
        assertEquals(
                200,
                call("GET", EVENT_GROUP, null, StationPermission.USER, StationPermission.EVENT_REGISTRATION)
                        .code());
    }

    @Test
    void somebodyOfAnotherStationCannotBeSignedUp() {
        when(eventGroups.isMemberHere(STATION_ID, 99)).thenReturn(false);
        String forOther = "{\"memberId\": 99, \"parts\": [101]}";

        var response = call(
                "POST", EVENT_GROUP + "/sign-up", forOther, StationPermission.USER, StationPermission.EVENT_MANAGER);

        assertEquals(EventRefusal.EVENT_GROUP_MEMBER_NOT_HERE, refusalOf(response));
    }

    @Test
    void aSignUpIsWithdrawnEvenWhereTheEventGroupIsNoLongerMeantForTheReader() {
        when(eventGroups.canSeeAny(eq(EVENT_GROUP_ID), anyList(), anySet())).thenReturn(false);

        assertEquals(
                200,
                call("DELETE", EVENT_GROUP + "/sign-up", null, StationPermission.USER)
                        .code());
        verify(signUps).withdraw(STATION_ID, EVENT_GROUP_ID, TestSessions.MEMBER_ID);
    }

    @Test
    void aMemberTakenOutOfTheAudienceCannotChangeTheirParts() {
        when(eventGroups.canSignUp(eq(EVENT_GROUP_ID), anyInt(), anySet())).thenReturn(false);

        var response = call("PUT", EVENT_GROUP + "/sign-up", "{\"parts\": [102]}", StationPermission.USER);

        assertEquals(EventRefusal.MEMBER_NOT_INVITED_TO_EVENT, refusalOf(response));
    }

    @Test
    void anAppointmentSaysWhichEventGroupItIsAPartOf() {
        var event = mock(StationEvent.class);
        when(event.id()).thenReturn(101);
        when(visibility.requireVisibleEvent(any(), eq(101))).thenReturn(event);
        when(visibility.requireVisibleEvent(any(), eq(102))).thenReturn(event);
        when(eventGroups.partSummary(STATION_ID, 101))
                .thenReturn(new EventGroupPartSummary(EVENT_GROUP_ID, "Jugendflamme 1", 5, "Übung", 2, 4, null));

        var part = call("GET", PREFIX + "/events/101/group", null, StationPermission.USER);
        assertEquals(2, json(part).get("number").asInt());
        when(eventGroups.partSummary(STATION_ID, 101)).thenReturn(null);
        assertEquals(
                204,
                call("GET", PREFIX + "/events/101/group", null, StationPermission.USER)
                        .code());
    }

    @Test
    void onlyWhoeverEditsAppointmentsCallsAnEventGroupOff() {
        assertEquals(
                403,
                call("POST", EVENT_GROUP + "/cancel", "{\"reason\": \"Krank\"}", StationPermission.USER)
                        .code());
        assertEquals(
                200,
                call("POST", EVENT_GROUP + "/cancel", "{\"reason\": \"Krank\"}", StationPermission.EVENT_EDIT)
                        .code());
        verify(cancellation).cancel(STATION_ID, EVENT_GROUP_ID, "Krank", TestSessions.MEMBER_ID);
    }

    @Test
    void whoeverEditsAppointmentsCreatesAPartStraightIntoTheEventGroup() {
        String part = """
                {"setId": 5, "name": "Samstag", "startTime": "2027-03-06T08:00:00Z",
                 "endTime": "2027-03-06T16:00:00Z"}""";
        when(partFactory.create(
                        eq(STATION_ID), eq(EVENT_GROUP_ID), eq(5), eq("Samstag"), isNull(), any(), any(), isNull()))
                .thenReturn(new EventGroupPart(101, 5, EVENT_GROUP_ID));

        assertEquals(
                403,
                call("POST", EVENT_GROUP + "/parts/new", part, StationPermission.USER)
                        .code());
        assertEquals(
                201,
                call("POST", EVENT_GROUP + "/parts/new", part, StationPermission.EVENT_EDIT)
                        .code());
    }

    @Test
    void anEventGroupStillOwedAnAnswerNamesWhoOwesIt() {
        var open = new EventGroup(
                EVENT_GROUP_ID,
                STATION_ID,
                "Jugendflamme 1",
                null,
                null,
                Instant.now().plusSeconds(3600),
                false,
                RestrictionMode.AND,
                RestrictionMode.AND,
                false,
                null,
                null,
                null);
        when(eventGroups.findOpenWithDeadline(STATION_ID)).thenReturn(List.of(open));
        when(guardians.household(any())).thenReturn(List.of(TestSessions.MEMBER_ID, CHILD_ID));
        when(signUps.findByEventGroup(EVENT_GROUP_ID))
                .thenReturn(List.of(new EventGroupRegistration(
                        1, EVENT_GROUP_ID, TestSessions.MEMBER_ID, RegistrationStatus.WITHDRAWN, Instant.now(), null)));

        var awaiting = json(call("GET", PREFIX + "/events/groups/awaiting", null, StationPermission.USER));

        assertEquals(1, awaiting.size());
        assertEquals(1, awaiting.get(0).get("members").size());
    }

    @Test
    void aNewPartWithoutTimesIsRefused() {
        var response = call(
                "POST",
                EVENT_GROUP + "/parts/new",
                "{\"setId\": 5, \"name\": \"Samstag\"}",
                StationPermission.EVENT_EDIT);

        assertEquals(EventRefusal.EVENT_GROUP_PART_NEEDS_TIMES, refusalOf(response));
    }

    @Test
    void savingTheEditorHandsSetsAndAudiencesOnInOneCall() {
        String body = """
                {"details": {"name": "Jugendflamme 1", "requiresConfirmation": false},
                 "sets": [{"name": "Übung", "minimum": 2}],
                 "registration": {"userTypes": [], "groupIds": [], "tagIds": [], "memberIds": [], "mode": "AND"},
                 "view": {"userTypes": [], "groupIds": [], "tagIds": [], "memberIds": [], "mode": "AND"}}""";
        var eventGroup = eventGroups.requireEventGroup(STATION_ID, EVENT_GROUP_ID);
        when(eventGroups.save(eq(STATION_ID), eq(EVENT_GROUP_ID), any(), any(), any(), any(), any()))
                .thenReturn(eventGroup);

        assertEquals(
                200,
                call("PUT", EVENT_GROUP, body, StationPermission.EVENT_EDIT).code());
        verify(eventGroups).save(eq(STATION_ID), eq(EVENT_GROUP_ID), any(), any(), any(), any(), any());
    }

    @Test
    void anEventGroupStartedFromAnAppointmentHandsItToTheCreation() {
        String body = """
                {"details": {"name": "Grundlehrgang", "requiresConfirmation": false},
                 "sets": [{"name": "Ausbildungsabende", "minimum": 6}],
                 "appointmentId": 12}""";

        assertEquals(
                201,
                call("POST", PREFIX + "/events/groups", body, StationPermission.EVENT_EDIT)
                        .code());
        verify(eventGroups).createWith(eq(STATION_ID), any(), any(), any(), any(), any(), eq(12));
    }
}
