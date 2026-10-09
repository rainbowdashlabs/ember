/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.service;

import dev.chojo.ember.api.StationSession;
import dev.chojo.ember.api.TestSessions;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.api.auth.InstanceUserType;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.api.auth.StepUpCategory;
import dev.chojo.ember.api.auth.StepUpGuard;
import dev.chojo.ember.api.refusal.MemberRefusal;
import dev.chojo.ember.api.refusal.Refusal;
import dev.chojo.ember.api.refusal.RefusalResponse;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.account.entity.AccountAction;
import dev.chojo.ember.feature.account.repository.AccountRepository;
import dev.chojo.ember.feature.account.service.AccountEmailService;
import dev.chojo.ember.feature.account.service.AccountReach;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
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
    private NameChangeService nameChanges;
    private AccountReach reach;
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

    private static StationSession managerHere() {
        return StationSession.of(manager());
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
        nameChanges = mock(NameChangeService.class);
        reach = mock(AccountReach.class);
        service = new MemberAccountService(
                accounts,
                members,
                auth,
                loginNames,
                emails,
                stepUp,
                mock(MemberNameResolver.class),
                nameChanges,
                reach);
        when(accounts.update(anyInt(), any(), any(), any())).thenReturn(true);
    }

    private void targetIsAtTheStation() {
        when(members.findByStationAndAccount(STATION_ID, TARGET))
                .thenReturn(Optional.of(new StationMember(
                        9, STATION_ID, null, TARGET, false, null, "Tom", StationUserType.MEMBER, null)));
    }

    @Test
    void anAccountOfAnotherStationIsNotThere() {
        assertEquals(MemberRefusal.MEMBER_NOT_HERE, refusalOf(() -> service.requireStationAccount(TARGET, STATION_ID)));
        assertEquals(MemberRefusal.MEMBER_NOT_HERE, refusalOf(() -> service.requireStationAccount(TARGET, null)));
    }

    @Test
    void anAdministratorIsOutOfReachOfAMemberManager() {
        targetIsAtTheStation();
        when(accounts.findById(TARGET)).thenReturn(Optional.of(target("tom@test.com", InstanceUserType.ADMINISTRATOR)));

        assertEquals(
                MemberRefusal.ACCOUNT_ABOVE_YOU,
                refusalOf(() -> service.actionableAccount(
                        TARGET,
                        managerHere(),
                        MemberRefusal.ACCOUNT_NOT_HERE_ON_PASSWORD_RESET,
                        AccountAction.PASSWORD_RESET)));
    }

    @Test
    void aVanishedAccountIsRefusedWithTheGivenRefusal() {
        targetIsAtTheStation();

        assertEquals(
                MemberRefusal.ACCOUNT_NOT_HERE_ON_PASSWORD_RESET,
                refusalOf(() -> service.actionableAccount(
                        TARGET,
                        managerHere(),
                        MemberRefusal.ACCOUNT_NOT_HERE_ON_PASSWORD_RESET,
                        AccountAction.PASSWORD_RESET)));
    }

    @Test
    void anAccountThatIsNotTheStationsAloneIsOutOfReach() {
        targetIsAtTheStation();
        when(accounts.findById(TARGET)).thenReturn(Optional.of(target("tom@test.com", InstanceUserType.USER)));
        doThrow(MemberRefusal.ACCOUNT_HELD_BY_AN_ASSOCIATION.raise())
                .when(reach)
                .require(STATION_ID, TARGET, AccountAction.ONBOARD_AGAIN);

        assertEquals(
                MemberRefusal.ACCOUNT_HELD_BY_AN_ASSOCIATION,
                refusalOf(() -> service.actionableAccount(
                        TARGET,
                        managerHere(),
                        MemberRefusal.ACCOUNT_NOT_HERE_ON_ONBOARDING_AGAIN,
                        AccountAction.ONBOARD_AGAIN)));
    }

    @Test
    void movingTheAddressOfASharedAccountIsRefusedBeforeAnythingIsWritten() {
        targetIsAtTheStation();
        when(accounts.findById(TARGET)).thenReturn(Optional.of(target("tom@test.com", InstanceUserType.USER)));
        doThrow(MemberRefusal.ACCOUNT_SHARED_WITH_ANOTHER_STATION.raise())
                .when(reach)
                .require(STATION_ID, TARGET, AccountAction.EMAIL_CHANGE);

        assertEquals(
                MemberRefusal.ACCOUNT_SHARED_WITH_ANOTHER_STATION,
                refusalOf(() -> update(manager(), TARGET, new UpdateAccountRequest("new@test.com", null, "A", "B"))));
        verify(accounts, never()).update(anyInt(), any(), any(), any());
        verify(emails, never()).setEmailFor(anyInt(), anyInt(), anyString());
    }

    @Test
    void changingTheSignInNameOfASharedAccountIsRefusedBeforeAnythingIsWritten() {
        targetIsAtTheStation();
        when(accounts.findById(TARGET)).thenReturn(Optional.of(target("tom@test.com", InstanceUserType.USER)));
        doThrow(MemberRefusal.ACCOUNT_HELD_BY_AN_ASSOCIATION.raise())
                .when(reach)
                .require(STATION_ID, TARGET, AccountAction.USERNAME_CHANGE);

        assertEquals(
                MemberRefusal.ACCOUNT_HELD_BY_AN_ASSOCIATION,
                refusalOf(() -> update(manager(), TARGET, new UpdateAccountRequest("tom@test.com", "tom", "A", "B"))));
        verify(accounts, never()).update(anyInt(), any(), any(), any());
        verify(accounts, never()).updateUsername(anyInt(), any());
    }

    @Test
    void changingTheSignInNameOfAnUnconfirmedAccountIsRefused() {
        targetIsAtTheStation();
        when(accounts.findById(TARGET)).thenReturn(Optional.of(target("tom@test.com", InstanceUserType.USER)));
        doThrow(MemberRefusal.ACCOUNT_NOT_CONFIRMED_YET.raise())
                .when(reach)
                .require(STATION_ID, TARGET, AccountAction.USERNAME_CHANGE);

        assertEquals(
                MemberRefusal.ACCOUNT_NOT_CONFIRMED_YET,
                refusalOf(() -> update(manager(), TARGET, new UpdateAccountRequest(null, "tom", "A", "B"))));
        verify(accounts, never()).updateUsername(anyInt(), any());
    }

    @Test
    void anUnchangedSignInNameIsNotAChangeToTheAccount() {
        targetIsAtTheStation();
        var existing = new Account(
                TARGET, null, "tom@test.com", "tom", "Tom", "Target", true, InstanceUserType.USER, "Tom", null, null);
        when(accounts.findById(TARGET)).thenReturn(Optional.of(existing));

        update(manager(), TARGET, new UpdateAccountRequest("tom@test.com", " tom ", "A", "B"));

        verify(reach, never()).require(STATION_ID, TARGET, AccountAction.USERNAME_CHANGE);
    }

    @Test
    void ownSignInNameIsNoStationMatter() {
        var self = TestSessions.member(STATION_ID);
        when(accounts.findById(self.accountId()))
                .thenReturn(Optional.of(new Account(
                        self.accountId(),
                        null,
                        "self@test.com",
                        null,
                        "A",
                        "B",
                        true,
                        InstanceUserType.USER,
                        "A B",
                        null,
                        null)));

        update(self, self.accountId(), new UpdateAccountRequest(null, "selfname", "A", "B"));

        verify(reach, never()).require(anyInt(), anyInt(), eq(AccountAction.USERNAME_CHANGE));
    }

    @Test
    void renamingAnAccountIsDecidedAsAnActionInsideTheStation() {
        targetIsAtTheStation();
        when(accounts.findById(TARGET)).thenReturn(Optional.of(target("tom@test.com", InstanceUserType.USER)));

        update(manager(), TARGET, new UpdateAccountRequest("tom@test.com", null, "A", "B"));

        verify(reach).require(STATION_ID, TARGET, AccountAction.RENAME);
        verify(reach, never()).require(STATION_ID, TARGET, AccountAction.EMAIL_CHANGE);
    }

    @Test
    void aPasskeyCodeIsOnlyForSomebodyWithoutAnAddress() {
        targetIsAtTheStation();
        when(accounts.findById(TARGET)).thenReturn(Optional.of(target("tom@test.com", InstanceUserType.USER)));
        assertEquals(
                MemberRefusal.MEMBER_HAS_OWN_ADDRESS,
                refusalOf(() -> service.addresslessAccount(TARGET, managerHere())));

        when(accounts.findById(TARGET)).thenReturn(Optional.of(target("tom@x.local", InstanceUserType.USER)));
        assertEquals(TARGET, service.addresslessAccount(TARGET, managerHere()).id());
    }

    @Test
    void somebodyElsesAccountNeedsTheRightToEditMembers() {
        var plainMember = TestSessions.member(STATION_ID);

        assertEquals(
                MemberRefusal.ACCOUNT_NOT_YOURS_TO_CHANGE,
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
                MemberRefusal.ACCOUNT_NOT_HERE_ON_CHANGE,
                refusalOf(() ->
                        update(TestSessions.administrator(), TARGET, new UpdateAccountRequest(null, null, "A", "B"))));

        when(accounts.findById(TARGET)).thenReturn(Optional.of(target("tom@test.com", InstanceUserType.USER)));
        when(accounts.update(anyInt(), any(), any(), any())).thenReturn(false);
        assertEquals(
                MemberRefusal.MEMBER_NOT_HERE_ON_CHANGE,
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
                MemberRefusal.ACCOUNT_ABOVE_YOU,
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
    void ownNewNameWaitsWhereItNeedsApproval() {
        int own = TestSessions.ACCOUNT_ID;
        var account = TestSessions.account();
        when(accounts.findById(own)).thenReturn(Optional.of(account));
        when(nameChanges.needsApproval(any())).thenReturn(true);

        var answer = update(
                TestSessions.member(STATION_ID), own, new UpdateAccountRequest(account.email(), null, "Neu", "Name"));

        assertTrue(answer.nameWaits());
        verify(accounts).update(own, account.email(), account.firstName(), account.lastName());
        verify(nameChanges).request(account, "Neu", "Name");
    }

    @Test
    void anUnchangedNameAsksNobody() {
        int own = TestSessions.ACCOUNT_ID;
        var account = TestSessions.account();
        when(accounts.findById(own)).thenReturn(Optional.of(account));
        when(nameChanges.needsApproval(any())).thenReturn(true);

        var answer = update(
                TestSessions.member(STATION_ID),
                own,
                new UpdateAccountRequest(account.email(), null, account.firstName(), account.lastName()));

        assertFalse(answer.nameWaits());
        verify(nameChanges, never()).request(any(), anyString(), anyString());
    }

    @Test
    void somebodyElsesNameIsWrittenAtOnce() {
        targetIsAtTheStation();
        when(accounts.findById(TARGET)).thenReturn(Optional.of(target("tom@test.com", InstanceUserType.USER)));
        when(nameChanges.needsApproval(any())).thenReturn(true);

        var answer = update(manager(), TARGET, new UpdateAccountRequest("tom@test.com", null, "Tim", "T"));

        assertFalse(answer.nameWaits());
        verify(accounts).update(TARGET, "tom@test.com", "Tim", "T");
        verify(nameChanges, never()).request(any(), anyString(), anyString());
    }

    @Test
    void anAddressSomebodyElseHoldsIsRefused() {
        int own = TestSessions.ACCOUNT_ID;
        when(accounts.findById(own)).thenReturn(Optional.of(TestSessions.account()));
        when(auth.requestEmailChange(own, "taken@test.com")).thenReturn(EmailChangeResult.DUPLICATE);

        assertEquals(
                MemberRefusal.ACCOUNT_ADDRESS_TAKEN,
                refusalOf(() -> update(manager(), own, new UpdateAccountRequest("taken@test.com", null, "A", "B"))));
    }
}
