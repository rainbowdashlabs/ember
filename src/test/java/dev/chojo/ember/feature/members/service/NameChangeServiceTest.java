/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.service;

import dev.chojo.ember.api.MemberIdentity;
import dev.chojo.ember.api.TestSessions;
import dev.chojo.ember.api.auth.InstanceUserType;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.api.refusal.MemberRefusal;
import dev.chojo.ember.api.refusal.Refusal;
import dev.chojo.ember.api.refusal.RefusalResponse;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.account.repository.AccountRepository;
import dev.chojo.ember.feature.members.entity.NameChangeOutcome;
import dev.chojo.ember.feature.members.entity.NameChangeRequest;
import dev.chojo.ember.feature.members.entity.PendingNameChange;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.repository.NameChangeRequestRepository;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import dev.chojo.ember.feature.notifications.entity.Delivery;
import dev.chojo.ember.feature.notifications.entity.NotificationData;
import dev.chojo.ember.feature.notifications.entity.NotificationParams;
import dev.chojo.ember.feature.notifications.entity.NotificationType;
import dev.chojo.ember.feature.notifications.entity.StationAudience;
import dev.chojo.ember.feature.notifications.service.Notifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** A member's new register name, waiting for a member manager at any of their stations. */
class NameChangeServiceTest {
    private static final int STATION = 3;
    private static final int OTHER_STATION = 4;
    private static final int ACCOUNT = TestSessions.ACCOUNT_ID;
    private static final int DECIDER = 77;
    private static final int REQUEST = 5;

    private NameChangeRequestRepository requests;
    private AccountRepository accounts;
    private StationMemberService memberService;
    private StationMemberRepository memberRepository;
    private MemberPermissionResolver permissions;
    private MemberNameResolver nameResolver;
    private Notifier notifier;
    private NameChangeService service;

    private static StationMember memberAt(int id, int stationId, boolean former) {
        return new StationMember(
                id,
                stationId,
                UUID.fromString("00000000-0000-0000-0000-%012d".formatted(id)),
                ACCOUNT,
                former,
                null,
                "Mara Nager",
                StationUserType.MEMBER,
                LocalDate.of(2020, 1, 1));
    }

    private static NameChangeRequest openRequest() {
        return new NameChangeRequest(REQUEST, ACCOUNT, "Mia", "Neu", Instant.EPOCH, null, null, null, null);
    }

    private static Account account() {
        return new Account(
                ACCOUNT,
                null,
                "mara@test.com",
                null,
                "Mara",
                "Nager",
                true,
                InstanceUserType.USER,
                "Mara Nager",
                null,
                null);
    }

    private static Refusal refusalOf(Executable call) {
        return assertThrows(RefusalResponse.class, call).refusal();
    }

    @BeforeEach
    void setup() {
        requests = mock(NameChangeRequestRepository.class);
        accounts = mock(AccountRepository.class);
        memberService = mock(StationMemberService.class);
        memberRepository = mock(StationMemberRepository.class);
        permissions = mock(MemberPermissionResolver.class);
        nameResolver = mock(MemberNameResolver.class);
        notifier = mock(Notifier.class);
        var identities = mock(MemberIdentityFactory.class);
        when(identities.enrich(any())).thenAnswer(call -> call.getArgument(0));
        service = new NameChangeService(
                requests, accounts, memberService, memberRepository, permissions, nameResolver, identities, notifier);
        when(permissions.resolve(any(StationMember.class))).thenReturn(EnumSet.noneOf(StationPermission.class));
    }

    private void belongsTo(StationMember... memberships) {
        when(memberService.findBelongingByAccount(ACCOUNT)).thenReturn(List.of(memberships));
    }

    private void currentlyAt(int stationId, StationMember member) {
        when(memberRepository.findByStationAndAccount(stationId, ACCOUNT)).thenReturn(Optional.of(member));
    }

    @Test
    void aPlainMemberWaitsForApproval() {
        belongsTo(memberAt(11, STATION, false));

        assertTrue(service.needsApproval(TestSessions.member(STATION)));
    }

    @Test
    void whoeverConfirmsChangesAtOneOfTheirStationsDoesNotWait() {
        var here = memberAt(11, STATION, false);
        var there = memberAt(12, OTHER_STATION, false);
        belongsTo(here, there);
        when(permissions.resolve(there)).thenReturn(EnumSet.of(StationPermission.MEMBER_CHANGES));

        assertFalse(service.needsApproval(TestSessions.member(STATION)));
    }

    @Test
    void anAdministratorAndAnAccountAtNoStationDoNotWait() {
        assertFalse(service.needsApproval(TestSessions.administrator()));

        belongsTo(memberAt(11, STATION, true));
        assertFalse(service.needsApproval(TestSessions.member(STATION)));
    }

    @Test
    void aRequestIsAnnouncedAtEveryStationTheMemberBelongsTo() {
        belongsTo(memberAt(11, STATION, false), memberAt(12, OTHER_STATION, false), memberAt(13, 9, true));
        when(requests.request(ACCOUNT, "Mia", "Neu")).thenReturn(openRequest());

        service.request(account(), "Mia", "Neu");

        var data = ArgumentCaptor.forClass(NotificationData.class);
        verify(notifier)
                .notify(
                        eq(StationAudience.holders(STATION, StationPermission.MEMBER_CHANGES)
                                .except(11)),
                        eq(NotificationType.NAME_CHANGE_REQUESTED),
                        data.capture(),
                        eq(Delivery.EVERY_TIME));
        verify(notifier)
                .notify(
                        eq(StationAudience.holders(OTHER_STATION, StationPermission.MEMBER_CHANGES)
                                .except(12)),
                        eq(NotificationType.NAME_CHANGE_REQUESTED),
                        any(),
                        any());
        verify(notifier, times(2)).notify(any(), any(), any(), any());
        assertEquals(
                new NotificationParams.NameChangeRequested("Mara Nager", "Mia Neu"),
                data.getValue().params());
    }

    @Test
    void approvingWritesTheNameAndTellsTheMember() {
        currentlyAt(STATION, memberAt(11, STATION, false));
        when(requests.findOpenById(REQUEST)).thenReturn(Optional.of(openRequest()));
        when(requests.decide(REQUEST, NameChangeOutcome.APPROVED, DECIDER, null))
                .thenReturn(true);
        when(accounts.findById(ACCOUNT)).thenReturn(Optional.of(account()));

        service.approve(STATION, DECIDER, REQUEST);

        verify(accounts).update(ACCOUNT, "mara@test.com", "Mia", "Neu");
        verify(nameResolver).forgetAccount(ACCOUNT);
        verify(notifier)
                .notify(
                        eq(StationAudience.member(11)),
                        eq(NotificationType.NAME_CHANGE_APPROVED),
                        any(),
                        eq(Delivery.EVERY_TIME));
    }

    @Test
    void aRequestOfSomebodyNotAtThisStationCannotBeDecidedHere() {
        when(requests.findOpenById(REQUEST)).thenReturn(Optional.of(openRequest()));
        when(memberRepository.findByStationAndAccount(STATION, ACCOUNT)).thenReturn(Optional.empty());

        assertEquals(MemberRefusal.NAME_CHANGE_NOT_OPEN, refusalOf(() -> service.approve(STATION, DECIDER, REQUEST)));
        currentlyAt(STATION, memberAt(11, STATION, true));
        assertEquals(
                MemberRefusal.NAME_CHANGE_NOT_OPEN, refusalOf(() -> service.deny(STATION, DECIDER, REQUEST, null)));
        verify(accounts, never()).update(anyInt(), any(), any(), any());
    }

    /** Two managers at two stations press at once: the second finds nothing open any more. */
    @Test
    void theSecondDecisionIsRefused() {
        currentlyAt(STATION, memberAt(11, STATION, false));
        when(requests.findOpenById(REQUEST)).thenReturn(Optional.of(openRequest()));
        when(requests.decide(anyInt(), any(), any(), any())).thenReturn(false);

        assertEquals(MemberRefusal.NAME_CHANGE_NOT_OPEN, refusalOf(() -> service.approve(STATION, DECIDER, REQUEST)));
        verify(accounts, never()).update(anyInt(), any(), any(), any());
        verify(notifier, never()).notify(any(), any(), any(), any());
    }

    @Test
    void denyingTellsTheMemberWithTheTrimmedReasonOrWithoutOne() {
        currentlyAt(STATION, memberAt(11, STATION, false));
        when(requests.findOpenById(REQUEST)).thenReturn(Optional.of(openRequest()));
        when(requests.decide(eq(REQUEST), eq(NameChangeOutcome.DENIED), eq(DECIDER), any()))
                .thenReturn(true);

        service.deny(STATION, DECIDER, REQUEST, "  Bitte mit Ausweis  ");
        service.deny(STATION, DECIDER, REQUEST, "   ");

        verify(requests).decide(REQUEST, NameChangeOutcome.DENIED, DECIDER, "Bitte mit Ausweis");
        verify(requests).decide(eq(REQUEST), eq(NameChangeOutcome.DENIED), eq(DECIDER), isNull());
        var data = ArgumentCaptor.forClass(NotificationData.class);
        verify(notifier, times(2))
                .notify(eq(StationAudience.member(11)), eq(NotificationType.NAME_CHANGE_DENIED), data.capture(), any());
        assertEquals(
                List.of(
                        new NotificationParams.NameChangeDenied("Mia Neu", "Bitte mit Ausweis"),
                        new NotificationParams.NameChangeDenied("Mia Neu", null)),
                data.getAllValues().stream().map(NotificationData::params).toList());
    }

    @Test
    void aReasonLongerThanANotificationCarriesIsRefused() {
        assertEquals(
                MemberRefusal.NAME_CHANGE_REASON_TOO_LONG,
                refusalOf(() -> service.deny(STATION, DECIDER, REQUEST, "x".repeat(501))));
    }

    @Test
    void aMemberTakesTheirRequestBackOnlyWhileItWaits() {
        when(requests.findOpenByAccount(ACCOUNT)).thenReturn(Optional.of(openRequest()));
        when(requests.decide(REQUEST, NameChangeOutcome.WITHDRAWN, null, null)).thenReturn(true);

        service.withdraw(ACCOUNT);

        when(requests.findOpenByAccount(ACCOUNT)).thenReturn(Optional.empty());
        assertEquals(MemberRefusal.NAME_CHANGE_NONE_WAITING, refusalOf(() -> service.withdraw(ACCOUNT)));
        when(requests.findOpenByAccount(ACCOUNT)).thenReturn(Optional.of(openRequest()));
        when(requests.decide(REQUEST, NameChangeOutcome.WITHDRAWN, null, null)).thenReturn(false);
        assertEquals(MemberRefusal.NAME_CHANGE_NONE_WAITING, refusalOf(() -> service.withdraw(ACCOUNT)));
    }

    @Test
    void theStationListNamesTheMemberWithTheStationsUid() {
        var stationUid = UUID.randomUUID();
        var memberUid = UUID.randomUUID();
        when(requests.findOpenAtStation(STATION))
                .thenReturn(
                        List.of(new PendingNameChange(REQUEST, 11, memberUid, "Mara Nager", "Mia Neu", Instant.EPOCH)));

        var open = service.openAt(STATION, stationUid);

        assertEquals(new MemberIdentity(stationUid, memberUid), open.getFirst().member());
        assertEquals("Mia Neu", open.getFirst().requestedName());
        assertEquals(service.openOf(ACCOUNT), Optional.empty());
    }
}
