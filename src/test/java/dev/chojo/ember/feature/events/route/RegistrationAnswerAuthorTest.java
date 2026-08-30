/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.route;

import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.account.repository.AccountRepository;
import dev.chojo.ember.feature.attendance.service.AttendanceService;
import dev.chojo.ember.feature.events.entity.EventRegistration;
import dev.chojo.ember.feature.events.entity.RegistrationStatus;
import dev.chojo.ember.feature.events.service.EventCrudService;
import dev.chojo.ember.feature.events.service.EventRegistrationFieldService;
import dev.chojo.ember.feature.events.service.EventRegistrationService;
import dev.chojo.ember.feature.events.service.EventRestrictionService;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import dev.chojo.ember.feature.members.service.MemberIdentityFactory;
import dev.chojo.ember.feature.members.service.MemberNameResolver;
import dev.chojo.ember.feature.members.service.StationMemberService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
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
    private static final int OWNER_MEMBER_ID = 11;
    private static final int OTHER_MEMBER_ID = 12;

    private StationMemberService memberService;
    private EventRegistrationRoutes routes;

    private static StationMember member(int id) {
        return new StationMember(
                id, STATION_ID, UUID.randomUUID(), id, false, null, "Mitglied " + id, StationUserType.MEMBER, null);
    }

    private static UserSession sessionOf(int memberId, StationPermission... permissions) {
        return new UserSession(
                new Account(1, null, "wer@test.com", null, "Wer", "Da", true, null, "Wer Da", null, null),
                1,
                STATION_ID,
                null,
                member(memberId),
                Set.of(permissions),
                Set.of(),
                null);
    }

    private static EventRegistration registrationOf(int memberId) {
        return new EventRegistration(
                5, 9, memberId, LocalDate.of(2026, 9, 2), RegistrationStatus.ACCEPTED, Instant.now(), null);
    }

    @BeforeEach
    void setup() {
        memberService = mock(StationMemberService.class);
        when(memberService.findManaged(anyInt())).thenReturn(List.of());
        routes = new EventRegistrationRoutes(
                mock(EventCrudService.class),
                mock(EventRegistrationService.class),
                mock(EventRestrictionService.class),
                mock(MemberNameResolver.class),
                memberService,
                mock(StationMemberRepository.class),
                mock(AccountRepository.class),
                mock(AttendanceService.class),
                mock(MemberIdentityFactory.class),
                mock(EventRegistrationFieldService.class));
    }

    /** Runs the check the way the route does, unwrapping what reflection wraps a refusal in. */
    private void check(UserSession session, EventRegistration registration) throws Exception {
        Method guard = EventRegistrationRoutes.class.getDeclaredMethod(
                "requireAnswerAuthor", UserSession.class, EventRegistration.class);
        guard.setAccessible(true);
        try {
            guard.invoke(routes, session, registration);
        } catch (InvocationTargetException wrapped) {
            throw assertInstanceOf(Exception.class, wrapped.getCause());
        }
    }

    @Test
    void whoeverRunsTheAppointmentMayCorrectAnAnswer() {
        var session = sessionOf(OTHER_MEMBER_ID, StationPermission.EVENT_EDIT);

        assertDoesNotThrow(() -> check(session, registrationOf(OWNER_MEMBER_ID)));
    }

    @Test
    void theMemberWhoAnsweredMayChangeTheirOwn() {
        var session = sessionOf(OWNER_MEMBER_ID);

        assertDoesNotThrow(() -> check(session, registrationOf(OWNER_MEMBER_ID)));
    }

    /** Deciding who gets a place has always carried this with it, and still does. */
    @Test
    void whoeverDecidesPlacesMayCorrectAnAnswer() {
        var session = sessionOf(OTHER_MEMBER_ID, StationPermission.EVENT_REGISTRATION);

        assertDoesNotThrow(() -> check(session, registrationOf(OWNER_MEMBER_ID)));
    }

    @Test
    void aGuardianMayChangeTheAnswerOfSomebodyTheyAnswerFor() {
        when(memberService.findManaged(OTHER_MEMBER_ID)).thenReturn(List.of(member(OWNER_MEMBER_ID)));
        var session = sessionOf(OTHER_MEMBER_ID);

        assertDoesNotThrow(() -> check(session, registrationOf(OWNER_MEMBER_ID)));
    }

    /** Somebody else's answer stays somebody else's, which is the whole point of the check. */
    @Test
    void anotherMemberIsTurnedAway() {
        var session = sessionOf(OTHER_MEMBER_ID);

        assertThrows(Exception.class, () -> check(session, registrationOf(OWNER_MEMBER_ID)));
    }
}
