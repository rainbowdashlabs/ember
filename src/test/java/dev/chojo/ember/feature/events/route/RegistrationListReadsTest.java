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
import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.feature.attendance.service.AttendanceService;
import dev.chojo.ember.feature.events.entity.AppointmentField;
import dev.chojo.ember.feature.events.entity.EventQuestionSettings;
import dev.chojo.ember.feature.events.entity.EventRegistration;
import dev.chojo.ember.feature.events.entity.RegistrationStatus;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.events.route.EventRegistrationRoutes.RegistrationResponse;
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
import dev.chojo.ember.feature.question.FieldType;
import dev.chojo.ember.feature.station.service.StationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A list of registrations reads what its rows share once for the list, not once per row.
 *
 * <p>Every row used to read its member, the member's account, whoever filed it and their account,
 * the appointment and the appointment's questions on its own, so a list of a hundred people cost
 * some six hundred round trips. The rows must still say exactly what they said before.
 */
class RegistrationListReadsTest {
    private static final int STATION_ID = 3;
    private static final int EVENT_ID = 9;
    private static final int GUARDIAN_ID = 500;
    private static final int FIELD_ID = 77;
    private static final LocalDate DATE = LocalDate.of(2026, 9, 2);
    private static final UUID STATION_UID = UUID.randomUUID();

    private StationMemberRepository memberRepository;
    private EventCrudService crudService;
    private EventFieldService eventFieldService;
    private EventRegistrationService registrationService;
    private EventRegistrationFieldService registrationFieldService;
    private EventRegistrationRoutes routes;

    private static StationMember member(int id) {
        return new StationMember(id, STATION_ID, UUID.randomUUID(), id, false, null, "", StationUserType.MEMBER, null);
    }

    private static EventRegistration placedByTheGuardian(int id, int memberId) {
        return new EventRegistration(
                id,
                EVENT_ID,
                memberId,
                DATE,
                RegistrationStatus.ACCEPTED,
                Instant.parse("2026-08-01T10:00:00Z"),
                GUARDIAN_ID,
                Instant.parse("2026-08-01T10:00:00Z"),
                null,
                true);
    }

    private static List<Integer> memberIds(int count) {
        return IntStream.rangeClosed(1, count).boxed().toList();
    }

    private static AppointmentField driversNaming(List<Integer> memberIds) {
        return new AppointmentField(
                FIELD_ID,
                EVENT_ID,
                "Fahrer",
                FieldType.MEMBER_LIST,
                EventQuestionSettings.empty(),
                memberIds.toString(),
                0,
                false,
                null,
                false);
    }

    private static MemberIdentity identityOf(int memberId) {
        return new MemberIdentity(STATION_UID, new UUID(0, memberId));
    }

    @BeforeEach
    void setup() {
        memberRepository = mock(StationMemberRepository.class);
        crudService = mock(EventCrudService.class);
        eventFieldService = mock(EventFieldService.class);
        registrationService = mock(EventRegistrationService.class);
        registrationFieldService = mock(EventRegistrationFieldService.class);
        var nameResolver = mock(MemberNameResolver.class);
        var identityFactory = mock(MemberIdentityFactory.class);

        StationEvent event = mock(StationEvent.class);
        when(event.name()).thenReturn("Zeltlager");
        when(crudService.findById(EVENT_ID)).thenReturn(Optional.of(event));
        when(registrationFieldService.findValuesByRegistration(anyList())).thenReturn(Map.of());
        when(registrationFieldService.requiredFieldIds(anyInt())).thenReturn(Set.of());
        when(nameResolver.called(anyInt())).thenAnswer(call -> "Mitglied " + call.getArgument(0));
        when(nameResolver.called(GUARDIAN_ID)).thenReturn("Greta Guardian");
        when(identityFactory.local(anyInt(), anyInt())).thenAnswer(call -> identityOf(call.getArgument(1)));

        routes = new EventRegistrationRoutes(
                crudService,
                registrationService,
                mock(EventRestrictionService.class),
                nameResolver,
                mock(GuardianPolicy.class),
                new RegistrationRowLookups.Reader(
                        memberRepository, crudService, eventFieldService, nameResolver, identityFactory),
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

    private void placed(List<Integer> memberIds) {
        when(memberRepository.findByIds(anyList()))
                .thenReturn(memberIds.stream()
                        .map(RegistrationListReadsTest::member)
                        .toList());
        when(eventFieldService.findByEvent(EVENT_ID, DATE)).thenReturn(List.of(driversNaming(memberIds)));
    }

    /** Asks for the date's list as somebody who runs the station's appointments. */
    private List<RegistrationResponse> list(List<EventRegistration> registrations) {
        when(registrationService.findByEventAndDate(EVENT_ID, DATE)).thenReturn(registrations);
        var harness = RouteHarness.serving(routes);
        var answer = harness.request(client -> client.get(
                RouteHarness.PREFIX + "/events/%d/registrations?date=%s".formatted(EVENT_ID, DATE),
                harness.as(TestSessions.member(STATION_ID, StationPermission.USER, StationPermission.EVENT_EDIT))));
        return List.of(RouteHarness.read(answer, RegistrationResponse[].class));
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 40})
    void theReadsDoNotGrowWithTheList(int size) {
        var ids = memberIds(size);
        placed(ids);

        var rows =
                list(ids.stream().map(id -> placedByTheGuardian(1000 + id, id)).toList());

        assertEquals(size, rows.size());
        verify(memberRepository, times(1)).findByIds(anyList());
        verify(memberRepository, never()).findById(anyInt());
        verify(crudService, times(1)).findById(EVENT_ID);
        verify(eventFieldService, times(1)).findByEvent(any(Integer.class), any(LocalDate.class));
        verify(registrationFieldService, times(1)).findValuesByRegistration(anyList());
        verify(registrationFieldService, times(1)).requiredFieldIds(EVENT_ID);
    }

    /** A child placed by their guardian through a question reads as it always did. */
    @Test
    void aRowPlacedByAGuardianThroughAQuestionReadsAsBefore() {
        placed(List.of(7));

        var row = list(List.of(placedByTheGuardian(1007, 7))).getFirst();

        assertEquals(
                new RegistrationResponse(
                        1007,
                        EVENT_ID,
                        7,
                        "Mitglied 7",
                        identityOf(7),
                        DATE,
                        RegistrationStatus.ACCEPTED,
                        Instant.parse("2026-08-01T10:00:00Z"),
                        "Greta Guardian",
                        List.of(),
                        "Zeltlager",
                        false,
                        true,
                        "Fahrer",
                        null),
                row);
    }

    /** A member the station no longer knows keeps an empty name and no identity, as before. */
    @Test
    void anUnknownMemberReadsWithAnEmptyName() {
        placed(List.of());

        var row = list(List.of(placedByTheGuardian(1008, 8))).getFirst();

        assertEquals("", row.memberName());
        assertNull(row.memberIdentity());
        assertNull(row.fieldName());
    }
}
