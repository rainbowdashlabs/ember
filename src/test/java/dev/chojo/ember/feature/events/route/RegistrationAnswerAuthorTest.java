/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.route;

import dev.chojo.ember.api.Refusal;
import dev.chojo.ember.api.RouteHarness;
import dev.chojo.ember.api.TestSessions;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.feature.attendance.service.AttendanceService;
import dev.chojo.ember.feature.events.entity.EventRegistration;
import dev.chojo.ember.feature.events.entity.RegistrationStatus;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.events.service.EventCrudService;
import dev.chojo.ember.feature.events.service.EventFieldService;
import dev.chojo.ember.feature.events.service.EventMemberTableService;
import dev.chojo.ember.feature.events.service.EventRegistrationFieldService;
import dev.chojo.ember.feature.events.service.EventRegistrationService;
import dev.chojo.ember.feature.events.service.EventRestrictionService;
import dev.chojo.ember.feature.events.service.OccurrenceCalendar;
import dev.chojo.ember.feature.events.service.RegistrationAnswerReminder;
import dev.chojo.ember.feature.events.service.RegistrationRowLookups;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import dev.chojo.ember.feature.members.service.GuardianPolicy;
import dev.chojo.ember.feature.members.service.MemberIdentityFactory;
import dev.chojo.ember.feature.members.service.MemberNameResolver;
import dev.chojo.ember.feature.members.service.MemberTableRenderer;
import dev.chojo.ember.feature.members.service.MemberTableService;
import dev.chojo.ember.feature.station.service.StationService;
import io.javalin.testtools.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Who may change the answers somebody gave when they signed up.
 *
 * <p>The answers are the member's, and were theirs alone to correct for as long as the station read
 * them. Whoever runs the appointment is the one they were collected for: a shirt size typed wrong is
 * read off this list while the shirts are ordered, and sending the manager away to ask the member to
 * fix it is how the list gets ordered from wrong.
 */
class RegistrationAnswerAuthorTest {
    private static final int STATION_ID = 3;
    private static final int EVENT_ID = 9;
    private static final int REGISTRATION_ID = 5;
    private static final int ASKING_MEMBER_ID = TestSessions.MEMBER_ID;
    private static final int OTHER_MEMBER_ID = 12;

    private StationMemberRepository memberRepository;
    private EventRegistrationService registrationService;
    private EventRegistrationFieldService registrationFieldService;
    private EventRegistrationRoutes routes;

    private static StationMember member(int id) {
        return new StationMember(
                id, STATION_ID, UUID.randomUUID(), id, false, null, "Mitglied " + id, StationUserType.MEMBER, null);
    }

    private static UserSession sessionWith(StationPermission... permissions) {
        return TestSessions.member(
                STATION_ID,
                Stream.concat(Stream.of(StationPermission.USER), Arrays.stream(permissions))
                        .toArray(StationPermission[]::new));
    }

    private static EventRegistration registrationOf(int memberId) {
        return new EventRegistration(
                REGISTRATION_ID,
                EVENT_ID,
                memberId,
                LocalDate.of(2026, 9, 2),
                RegistrationStatus.ACCEPTED,
                Instant.now(),
                null);
    }

    @BeforeEach
    void setup() {
        memberRepository = mock(StationMemberRepository.class);
        when(memberRepository.findManaged(anyInt())).thenReturn(List.of());
        registrationService = mock(EventRegistrationService.class);
        registrationFieldService = mock(EventRegistrationFieldService.class);
        var crudService = mock(EventCrudService.class);
        var event = mock(StationEvent.class);
        when(event.stationId()).thenReturn(STATION_ID);
        when(crudService.findById(EVENT_ID)).thenReturn(Optional.of(event));
        routes = new EventRegistrationRoutes(
                crudService,
                registrationService,
                mock(EventRestrictionService.class),
                mock(MemberNameResolver.class),
                new GuardianPolicy(memberRepository),
                new RegistrationRowLookups.Reader(
                        memberRepository,
                        crudService,
                        mock(EventFieldService.class),
                        mock(MemberNameResolver.class),
                        mock(MemberIdentityFactory.class)),
                mock(AttendanceService.class),
                registrationFieldService,
                mock(RegistrationAnswerReminder.class),
                mock(EventMemberTableService.class),
                mock(MemberTableService.class),
                mock(MemberTableRenderer.class),
                mock(StationService.class),
                mock(OccurrenceCalendar.class),
                mock(EventVisibility.class));
    }

    private Response change(UserSession session, EventRegistration registration) {
        when(registrationService.findById(REGISTRATION_ID)).thenReturn(Optional.of(registration));
        var harness = RouteHarness.serving(routes);
        return harness.request(client -> client.put(
                RouteHarness.PREFIX + "/events/registrations/%d/fields".formatted(REGISTRATION_ID),
                RouteHarness.body("{\"fields\": []}"),
                harness.as(session)));
    }

    private void assertChanged(Response answer) {
        assertEquals(200, answer.code(), answer.body().string());
        verify(registrationFieldService).replaceAnswers(eq(EVENT_ID), eq(REGISTRATION_ID), anyMap(), anyBoolean());
    }

    @Test
    void whoeverRunsTheAppointmentMayCorrectAnAnswer() {
        assertChanged(change(sessionWith(StationPermission.EVENT_EDIT), registrationOf(OTHER_MEMBER_ID)));
    }

    @Test
    void theMemberWhoAnsweredMayChangeTheirOwn() {
        assertChanged(change(sessionWith(), registrationOf(ASKING_MEMBER_ID)));
    }

    /** Deciding who gets a place has always carried this with it, and still does. */
    @Test
    void whoeverDecidesPlacesMayCorrectAnAnswer() {
        assertChanged(change(sessionWith(StationPermission.EVENT_REGISTRATION), registrationOf(OTHER_MEMBER_ID)));
    }

    @Test
    void aGuardianMayChangeTheAnswerOfSomebodyTheyAnswerFor() {
        when(memberRepository.findManaged(ASKING_MEMBER_ID)).thenReturn(List.of(member(OTHER_MEMBER_ID)));

        assertChanged(change(sessionWith(), registrationOf(OTHER_MEMBER_ID)));
    }

    /** Somebody else's answer stays somebody else's, which is the whole point of the check. */
    @Test
    void anotherMemberIsTurnedAway() {
        var answer = change(sessionWith(), registrationOf(OTHER_MEMBER_ID));

        assertEquals(Refusal.REGISTRATION_ANSWERS_NOT_YOURS, RouteHarness.refusalOf(answer));
        verify(registrationFieldService, never()).replaceAnswers(anyInt(), anyInt(), anyMap(), anyBoolean());
    }
}
