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
import dev.chojo.ember.api.refusal.EventRefusal;
import dev.chojo.ember.feature.attendance.service.AttendanceService;
import dev.chojo.ember.feature.events.entity.EventRegistration;
import dev.chojo.ember.feature.events.entity.RegistrationDeadline;
import dev.chojo.ember.feature.events.entity.RegistrationStatus;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.events.repository.EventBreakRepository;
import dev.chojo.ember.feature.events.repository.EventDateCancellationRepository;
import dev.chojo.ember.feature.events.repository.EventDetachedDateRepository;
import dev.chojo.ember.feature.events.repository.EventRepository;
import dev.chojo.ember.feature.events.service.AwaitingAnswers;
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
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import dev.chojo.ember.feature.members.service.GuardianPolicy;
import dev.chojo.ember.feature.members.service.MemberIdentityFactory;
import dev.chojo.ember.feature.members.service.MemberNameResolver;
import dev.chojo.ember.feature.members.service.MemberTableRenderer;
import dev.chojo.ember.feature.members.service.MemberTableService;
import dev.chojo.ember.feature.restriction.RestrictionMode;
import dev.chojo.ember.feature.station.repository.StationRepository;
import dev.chojo.ember.feature.station.service.StationService;
import io.javalin.testtools.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A relative deadline closes each date of a series on its own at the member's door: the date whose
 * deadline passed is refused, the next date still takes sign-ups and changed answers, and whoever runs
 * the appointment is not held to the deadline.
 */
class RegistrationDeadlineRoutesTest {
    private static final int STATION_ID = 3;
    private static final int EVENT_ID = 9;
    private static final int REGISTRATION_ID = 5;
    private static final int MEMBER_ID = TestSessions.MEMBER_ID;

    private final LocalDate near = LocalDate.now(ZoneOffset.UTC).plusDays(1);
    private final LocalDate next = near.plusWeeks(1);

    private EventRegistrationService registrationService;
    private EventRegistrationRoutes routes;

    private static UserSession sessionWith(StationPermission... permissions) {
        return TestSessions.member(
                STATION_ID,
                Stream.concat(Stream.of(StationPermission.USER), Arrays.stream(permissions))
                        .toArray(StationPermission[]::new));
    }

    private StationEvent weeklyClosingThreeDaysAhead() {
        Instant start = near.minusWeeks(2).atTime(18, 0).toInstant(ZoneOffset.UTC);
        return new StationEvent(
                EVENT_ID,
                STATION_ID,
                "Wochendienst",
                null,
                StationEvent.EventType.RECURRING,
                near.getDayOfWeek().getValue(),
                start,
                start.plusSeconds(7200),
                null,
                true,
                RegistrationDeadline.relative(3 * 24 * 60, RegistrationDeadline.DeadlineAnchor.EXACT),
                false,
                null,
                RestrictionMode.AND,
                RestrictionMode.AND,
                false,
                null,
                null,
                false,
                null,
                null,
                null,
                null,
                null,
                null);
    }

    @BeforeEach
    void setup() {
        var memberRepository = mock(StationMemberRepository.class);
        when(memberRepository.findManaged(anyInt())).thenReturn(List.of());
        registrationService = mock(EventRegistrationService.class);
        var restrictions = mock(EventRestrictionService.class);
        when(restrictions.canRegister(anyInt(), anyInt(), any())).thenReturn(true);
        var crudService = mock(EventCrudService.class);
        when(crudService.findById(EVENT_ID)).thenReturn(Optional.of(weeklyClosingThreeDaysAhead()));
        var calendar = new OccurrenceCalendar(
                mock(EventRepository.class),
                mock(EventBreakRepository.class),
                mock(EventDateCancellationRepository.class),
                mock(EventDetachedDateRepository.class),
                mock(StationRepository.class));
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
                mock(EventRegistrationFieldService.class),
                mock(RegistrationAnswerReminder.class),
                mock(EventMemberTableService.class),
                mock(MemberTableService.class),
                mock(MemberTableRenderer.class),
                mock(StationService.class),
                calendar,
                mock(EventVisibility.class),
                new EventSignUpGuards(new GuardianPolicy(memberRepository)),
                mock(AwaitingAnswers.class));
        when(registrationService.register(anyInt(), anyInt(), any(), anyBoolean(), any(), anyBoolean(), any()))
                .thenAnswer(call -> new EventRegistration(
                        REGISTRATION_ID,
                        EVENT_ID,
                        call.getArgument(1),
                        call.getArgument(2),
                        RegistrationStatus.ACCEPTED,
                        Instant.now(),
                        null));
        when(registrationService.answerComing(anyInt(), anyBoolean(), any())).thenReturn(true);
    }

    private Response register(LocalDate date, UserSession session) {
        var harness = RouteHarness.serving(routes);
        return harness.request(client -> client.post(
                RouteHarness.PREFIX + "/events/%d/register".formatted(EVENT_ID),
                RouteHarness.body("{\"eventDate\": \"%s\"}".formatted(date)),
                harness.as(session)));
    }

    private Response answerComing(LocalDate date) {
        when(registrationService.findById(REGISTRATION_ID))
                .thenReturn(Optional.of(new EventRegistration(
                        REGISTRATION_ID, EVENT_ID, MEMBER_ID, date, RegistrationStatus.DECLINED, Instant.now(), null)));
        var harness = RouteHarness.serving(routes);
        return harness.request(client -> client.put(
                RouteHarness.PREFIX + "/events/registrations/%d/answer".formatted(REGISTRATION_ID),
                RouteHarness.body("{\"attending\": true}"),
                harness.as(sessionWith())));
    }

    @Test
    void aMemberIsRefusedOnlyOnTheDateWhoseDeadlinePassed() {
        var closed = register(near, sessionWith());
        assertEquals(EventRefusal.REGISTRATION_CLOSED, RouteHarness.refusalOf(closed));

        var open = register(next, sessionWith());
        assertEquals(201, open.code(), open.body().string());
        verify(registrationService)
                .register(eq(EVENT_ID), eq(MEMBER_ID), eq(next), anyBoolean(), any(), anyBoolean(), any());
        verify(registrationService, never())
                .register(anyInt(), anyInt(), eq(near), anyBoolean(), any(), anyBoolean(), any());
    }

    @Test
    void anAnswerIsChangedOnlyWhileItsDateIsOpen() {
        var closed = answerComing(near);
        assertEquals(EventRefusal.REGISTRATION_CLOSED_ON_ANSWER_CHANGE, RouteHarness.refusalOf(closed));

        var open = answerComing(next);
        assertEquals(204, open.code(), open.body().string());
    }

    @Test
    void whoeverRunsTheAppointmentSignsUpAfterTheDeadline() {
        var answer = register(near, sessionWith(StationPermission.EVENT_MANAGER));

        assertEquals(201, answer.code(), answer.body().string());
        verify(registrationService)
                .register(eq(EVENT_ID), eq(MEMBER_ID), eq(near), anyBoolean(), any(), anyBoolean(), any());
    }
}
