/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.service;

import dev.chojo.ember.api.Refusal;
import dev.chojo.ember.api.RefusalResponse;
import dev.chojo.ember.api.TestSessions;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.api.auth.InstanceUserType;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.api.auth.StepUpCategory;
import dev.chojo.ember.api.auth.StepUpGuard;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.account.repository.AccountRepository;
import dev.chojo.ember.feature.account.service.AccountEmailService;
import dev.chojo.ember.feature.account.service.AuthService;
import dev.chojo.ember.feature.account.service.AuthService.EmailChangeResult;
import dev.chojo.ember.feature.account.service.LoginNameService;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import dev.chojo.ember.feature.members.service.MemberAccountService.UpdateAccountRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MemberAccountServiceTest {
    private static final int STATION_ID = 3;
    private static final int TARGET = 42;

    private AccountRepository accounts;
    private StationMemberRepository members;
    private AuthService auth;
    private AccountEmailService emails;
    private StepUpGuard stepUp;
    private LoginNameService loginNames;
    private MemberAccountService service;

    private static Account target(String email, InstanceUserType type) {
        return new Account(TARGET, null, email, null, "Tom", "Target", true, type, "Tom Target", null, null);
    }

    private static Refusal refusalOf(Executable call) {
        return assertThrows(RefusalResponse.class, call).refusal();
    }

    private static UserSession manager() {
        return TestSessions.member(STATION_ID, StationPermission.MEMBER_EDIT);
    }

    private MemberAccountService.UpdateAccountResponse update(
            UserSession session, int accountId, UpdateAccountRequest request) {
        return service.update(session, session.stationId(), accountId, request);
    }

    @BeforeEach
    void setup() {
        accounts = mock(AccountRepository.class);
        members = mock(StationMemberRepository.class);
        auth = mock(AuthService.class);
        emails = mock(AccountEmailService.class);
        stepUp = mock(StepUpGuard.class);
        loginNames = mock(LoginNameService.class);
        service = new MemberAccountService(
                accounts, members, auth, loginNames, emails, stepUp, mock(MemberNameResolver.class));
        when(accounts.update(anyInt(), any(), any(), any())).thenReturn(true);
    }

    private void targetIsAtTheStation() {
        when(members.findByStationAndAccount(STATION_ID, TARGET))
                .thenReturn(Optional.of(new StationMember(
                        9, STATION_ID, null, TARGET, false, null, "Tom", StationUserType.MEMBER, null)));
    }

    @Test
    void anAccountOfAnotherStationIsNotThere() {
        assertEquals(Refusal.MEMBER_NOT_HERE, refusalOf(() -> service.requireStationAccount(TARGET, STATION_ID)));
        assertEquals(Refusal.MEMBER_NOT_HERE, refusalOf(() -> service.requireStationAccount(TARGET, null)));
    }

    @Test
    void anAdministratorIsOutOfReachOfAMemberManager() {
        targetIsAtTheStation();
        when(accounts.findById(TARGET)).thenReturn(Optional.of(target("tom@test.com", InstanceUserType.ADMINISTRATOR)));

        assertEquals(
                Refusal.ACCOUNT_ABOVE_YOU,
                refusalOf(() ->
                        service.actionableAccount(TARGET, manager(), Refusal.ACCOUNT_NOT_HERE_ON_PASSWORD_RESET)));
    }

    @Test
    void aVanishedAccountIsRefusedWithTheGivenRefusal() {
        targetIsAtTheStation();

        assertEquals(
                Refusal.ACCOUNT_NOT_HERE_ON_PASSWORD_RESET,
                refusalOf(() ->
                        service.actionableAccount(TARGET, manager(), Refusal.ACCOUNT_NOT_HERE_ON_PASSWORD_RESET)));
    }

    @Test
    void aPasskeyCodeIsOnlyForSomebodyWithoutAnAddress() {
        targetIsAtTheStation();
        when(accounts.findById(TARGET)).thenReturn(Optional.of(target("tom@test.com", InstanceUserType.USER)));
        assertEquals(Refusal.MEMBER_HAS_OWN_ADDRESS, refusalOf(() -> service.addresslessAccount(TARGET, manager())));

        when(accounts.findById(TARGET)).thenReturn(Optional.of(target("tom@x.local", InstanceUserType.USER)));
        assertEquals(TARGET, service.addresslessAccount(TARGET, manager()).id());
    }

    @Test
    void somebodyElsesAccountNeedsTheRightToEditMembers() {
        var plainMember = TestSessions.member(STATION_ID);

        assertEquals(
                Refusal.ACCOUNT_NOT_YOURS_TO_CHANGE,
                refusalOf(() -> update(plainMember, TARGET, new UpdateAccountRequest(null, null, "A", "B"))));
    }

    @Test
    void aNameChangeLeavesTheAddressAndSetsTheSignInName() {
        targetIsAtTheStation();
        var existing = target("tom@test.com", InstanceUserType.USER);
        when(accounts.findById(TARGET)).thenReturn(Optional.of(existing));
        when(loginNames.validatedFor(existing, "tom")).thenReturn("tom");

        var answer = update(manager(), TARGET, new UpdateAccountRequest("TOM@test.com", "tom", "Tim", "T"));

        assertNull(answer.emailChange());
        verify(accounts).update(TARGET, "tom@test.com", "Tim", "T");
        verify(accounts).updateUsername(TARGET, "tom");
    }

    @Test
    void anAccountThatIsGoneOrWasNotWrittenIsRefused() {
        assertEquals(
                Refusal.ACCOUNT_NOT_HERE_ON_CHANGE,
                refusalOf(() ->
                        update(TestSessions.administrator(), TARGET, new UpdateAccountRequest(null, null, "A", "B"))));

        when(accounts.findById(TARGET)).thenReturn(Optional.of(target("tom@test.com", InstanceUserType.USER)));
        when(accounts.update(anyInt(), any(), any(), any())).thenReturn(false);
        assertEquals(
                Refusal.MEMBER_NOT_HERE_ON_CHANGE,
                refusalOf(() ->
                        update(TestSessions.administrator(), TARGET, new UpdateAccountRequest(null, null, "A", "B"))));
    }

    @Test
    void movingSomebodyElsesAddressTakesAFreshProofAndCommitsAtOnce() {
        targetIsAtTheStation();
        when(accounts.findById(TARGET)).thenReturn(Optional.of(target("tom@test.com", InstanceUserType.USER)));

        var answer = update(manager(), TARGET, new UpdateAccountRequest("new@test.com", null, "A", "B"));

        assertEquals(EmailChangeResult.COMMITTED, answer.emailChange());
        verify(stepUp).require(any(), eq(StepUpCategory.ACCOUNT_SECURITY));
        verify(emails).setEmailFor(TestSessions.ACCOUNT_ID, TARGET, "new@test.com");
    }

    @Test
    void movingAnAdministratorsAddressIsRefusedToAMemberManager() {
        targetIsAtTheStation();
        when(accounts.findById(TARGET)).thenReturn(Optional.of(target("tom@test.com", InstanceUserType.ADMINISTRATOR)));

        assertEquals(
                Refusal.ACCOUNT_ABOVE_YOU,
                refusalOf(() -> update(manager(), TARGET, new UpdateAccountRequest("new@test.com", null, "A", "B"))));
        verify(emails, never()).setEmailFor(anyInt(), anyInt(), anyString());
    }

    @Test
    void movingOnesOwnAddressWaitsForTheConfirmation() {
        int own = TestSessions.ACCOUNT_ID;
        when(accounts.findById(own)).thenReturn(Optional.of(TestSessions.account()));
        when(auth.requestEmailChange(own, "new@test.com")).thenReturn(EmailChangeResult.WAITING);

        var answer = update(manager(), own, new UpdateAccountRequest("new@test.com", null, "A", "B"));

        assertEquals(EmailChangeResult.WAITING, answer.emailChange());
    }

    @Test
    void anAddressSomebodyElseHoldsIsRefused() {
        int own = TestSessions.ACCOUNT_ID;
        when(accounts.findById(own)).thenReturn(Optional.of(TestSessions.account()));
        when(auth.requestEmailChange(own, "taken@test.com")).thenReturn(EmailChangeResult.DUPLICATE);

        assertEquals(
                Refusal.ACCOUNT_ADDRESS_TAKEN,
                refusalOf(() -> update(manager(), own, new UpdateAccountRequest("taken@test.com", null, "A", "B"))));
    }
}
