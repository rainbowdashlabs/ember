/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.route;

import dev.chojo.ember.api.RouteHarness;
import dev.chojo.ember.api.TestSessions;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.feature.attendance.service.AttendanceService;
import dev.chojo.ember.feature.events.entity.EventRegistration;
import dev.chojo.ember.feature.events.entity.RegistrationStatus;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.events.entity.WhenFull;
import dev.chojo.ember.feature.events.service.EventCrudService;
import dev.chojo.ember.feature.events.service.EventFieldService;
import dev.chojo.ember.feature.events.service.EventMemberTableService;
import dev.chojo.ember.feature.events.service.EventRegistrationFieldService;
import dev.chojo.ember.feature.events.service.EventRegistrationService;
import dev.chojo.ember.feature.events.service.EventRestrictionService;
import dev.chojo.ember.feature.events.service.EventSignUpGuards;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** What whoever runs an appointment chooses when they let somebody in on a full date, as the routes pass it on. */
class RegistrationPlaceRoutesTest {
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
        var restrictions = mock(EventRestrictionService.class);
        when(restrictions.canRegister(anyInt(), anyInt(), any())).thenReturn(true);
        registrationFieldService = mock(EventRegistrationFieldService.class);
        var crudService = mock(EventCrudService.class);
        var event = mock(StationEvent.class);
        when(event.stationId()).thenReturn(STATION_ID);
        when(event.requiresRegistration()).thenReturn(true);
        when(crudService.findById(EVENT_ID)).thenReturn(Optional.of(event));
        routes = new EventRegistrationRoutes(
                crudService,
                registrationService,
                restrictions,
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
                mock(EventVisibility.class),
                new EventSignUpGuards(new GuardianPolicy(memberRepository)));
    }

    private Response send(String path, String json, UserSession session, boolean post) {
        when(registrationService.findById(REGISTRATION_ID)).thenReturn(Optional.of(registrationOf(OTHER_MEMBER_ID)));
        var harness = RouteHarness.serving(routes);
        return harness.request(client -> post
                ? client.post(RouteHarness.PREFIX + path, RouteHarness.body(json), harness.as(session))
                : client.put(RouteHarness.PREFIX + path, RouteHarness.body(json), harness.as(session)));
    }

    @Test
    void acceptingBeyondTheLimitIsPassedOn() {
        when(registrationService.accept(REGISTRATION_ID, true)).thenReturn(true);

        var answer = send(
                "/events/registrations/%d/status".formatted(REGISTRATION_ID),
                "{\"status\": \"ACCEPTED\", \"overLimit\": true}",
                sessionWith(StationPermission.EVENT_REGISTRATION),
                false);

        assertEquals(200, answer.code(), answer.body().string());
        verify(registrationService).accept(REGISTRATION_ID, true);
    }

    @Test
    void acceptingWithoutSayingSoStaysWithinTheLimit() {
        when(registrationService.accept(REGISTRATION_ID, false)).thenReturn(true);

        var answer = send(
                "/events/registrations/%d/status".formatted(REGISTRATION_ID),
                "{\"status\": \"ACCEPTED\"}",
                sessionWith(StationPermission.EVENT_REGISTRATION),
                false);

        assertEquals(200, answer.code(), answer.body().string());
        verify(registrationService).accept(REGISTRATION_ID, false);
    }

    @Test
    void turningDownStaysAPlainStatusChange() {
        when(registrationService.updateStatus(REGISTRATION_ID, RegistrationStatus.DENIED))
                .thenReturn(true);

        var answer = send(
                "/events/registrations/%d/status".formatted(REGISTRATION_ID),
                "{\"status\": \"DENIED\"}",
                sessionWith(StationPermission.EVENT_REGISTRATION),
                false);

        assertEquals(200, answer.code(), answer.body().string());
        verify(registrationService).updateStatus(REGISTRATION_ID, RegistrationStatus.DENIED);
    }

    @Test
    void whoeverRunsTheAppointmentAnswersComingWithTheirChoice() {
        when(registrationService.answerComing(REGISTRATION_ID, true, WhenFull.QUEUE))
                .thenReturn(true);

        var answer = send(
                "/events/registrations/%d/answer".formatted(REGISTRATION_ID),
                "{\"attending\": true, \"whenFull\": \"QUEUE\"}",
                sessionWith(StationPermission.EVENT_MANAGER),
                false);

        assertEquals(204, answer.code(), answer.body().string());
        verify(registrationService).answerComing(REGISTRATION_ID, true, WhenFull.QUEUE);
    }

    @Test
    void aGuardianAnswersComingLikeTheMember() {
        when(memberRepository.findManaged(ASKING_MEMBER_ID)).thenReturn(List.of(member(OTHER_MEMBER_ID)));
        when(registrationService.answerComing(REGISTRATION_ID, false, null)).thenReturn(true);

        var answer = send(
                "/events/registrations/%d/answer".formatted(REGISTRATION_ID),
                "{\"attending\": true}",
                sessionWith(),
                false);

        assertEquals(204, answer.code(), answer.body().string());
        verify(registrationService).answerComing(REGISTRATION_ID, false, null);
    }

    @Test
    void whoeverRunsTheAppointmentSignsSomebodyUpWithTheirChoice() {
        when(registrationService.register(
                        eq(EVENT_ID),
                        eq(OTHER_MEMBER_ID),
                        any(),
                        eq(true),
                        eq(ASKING_MEMBER_ID),
                        eq(true),
                        eq(WhenFull.OVER_LIMIT)))
                .thenReturn(registrationOf(OTHER_MEMBER_ID));

        var answer = send(
                "/events/%d/register".formatted(EVENT_ID),
                "{\"memberId\": %d, \"whenFull\": \"OVER_LIMIT\"}".formatted(OTHER_MEMBER_ID),
                sessionWith(StationPermission.EVENT_MANAGER),
                true);

        assertEquals(201, answer.code(), answer.body().string());
        verify(registrationService)
                .register(
                        eq(EVENT_ID),
                        eq(OTHER_MEMBER_ID),
                        any(),
                        eq(true),
                        eq(ASKING_MEMBER_ID),
                        eq(true),
                        eq(WhenFull.OVER_LIMIT));
    }

    /**
     * Whatever a member sends, the route marks the sign-up as their own, which the service never lets
     * beyond the limit.
     */
    @Test
    void aMemberSigningThemselvesUpHasNoChoiceToMake() {
        when(registrationService.register(
                        eq(EVENT_ID), eq(ASKING_MEMBER_ID), any(), eq(true), isNull(), eq(false), any()))
                .thenReturn(registrationOf(ASKING_MEMBER_ID));

        var answer =
                send("/events/%d/register".formatted(EVENT_ID), "{\"whenFull\": \"OVER_LIMIT\"}", sessionWith(), true);

        assertEquals(201, answer.code(), answer.body().string());
        verify(registrationService)
                .register(eq(EVENT_ID), eq(ASKING_MEMBER_ID), any(), eq(true), isNull(), eq(false), any());
    }
}
