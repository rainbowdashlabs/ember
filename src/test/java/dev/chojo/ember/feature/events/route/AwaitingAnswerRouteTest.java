/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.route;

import dev.chojo.ember.api.RouteHarness;
import dev.chojo.ember.api.TestSessions;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.feature.attendance.service.AttendanceService;
import dev.chojo.ember.feature.events.entity.AwaitingAnswer;
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
import dev.chojo.ember.feature.station.service.StationService;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** The answers a household owes read one entry per appointment and date, naming the date. */
class AwaitingAnswerRouteTest {
    private static final int STATION_ID = 3;
    private static final int EVENT_ID = 9;
    private static final LocalDate NEAR = LocalDate.of(2026, 12, 2);
    private static final LocalDate NEXT = NEAR.plusWeeks(1);

    private static AwaitingAnswer owed(LocalDate date, int memberId) {
        var start = date.atTime(18, 0).toInstant(java.time.ZoneOffset.UTC);
        return new AwaitingAnswer(EVENT_ID, "Übung", date, start, start.minusSeconds(172_800), null, memberId);
    }

    @Test
    void membersOwingTheSameDateShareAnEntryAndAnotherDateHasItsOwn() {
        var awaiting = mock(AwaitingAnswers.class);
        when(awaiting.find(any())).thenReturn(List.of(owed(NEAR, 10), owed(NEAR, 11), owed(NEXT, 12)));
        var guardians = mock(GuardianPolicy.class);
        when(guardians.household(any())).thenReturn(List.of(10, 11, 12));
        var restrictions = mock(EventRestrictionService.class);
        when(restrictions.canRegister(anyInt(), anyInt(), any())).thenReturn(true);
        var names = mock(MemberNameResolver.class);
        when(names.called(anyInt())).thenAnswer(call -> "Mitglied " + call.getArgument(0));
        var crud = mock(EventCrudService.class);
        var members = mock(StationMemberRepository.class);
        var routes = new EventRegistrationRoutes(
                crud,
                mock(EventRegistrationService.class),
                restrictions,
                names,
                guardians,
                new RegistrationRowLookups.Reader(
                        members, crud, mock(EventFieldService.class), names, mock(MemberIdentityFactory.class)),
                mock(AttendanceService.class),
                mock(EventRegistrationFieldService.class),
                mock(RegistrationAnswerReminder.class),
                mock(EventMemberTableService.class),
                mock(MemberTableService.class),
                mock(MemberTableRenderer.class),
                mock(StationService.class),
                mock(OccurrenceCalendar.class),
                mock(EventVisibility.class),
                new EventSignUpGuards(guardians),
                awaiting);

        var harness = RouteHarness.serving(routes);
        var answer = harness.request(client -> client.get(
                RouteHarness.PREFIX + "/events/registrations/awaiting",
                harness.as(TestSessions.member(STATION_ID, StationPermission.USER))));
        var entries = List.of(RouteHarness.read(answer, EventRegistrationRoutes.AwaitingAnswer[].class));

        assertEquals(
                List.of(NEAR, NEXT),
                entries.stream()
                        .map(EventRegistrationRoutes.AwaitingAnswer::eventDate)
                        .toList());
        assertEquals(2, entries.getFirst().members().size());
        assertEquals(12, entries.get(1).members().getFirst().memberId());
        assertEquals(Instant.parse("2026-12-07T18:00:00Z"), entries.get(1).registrationClosesAt());
    }
}
