/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.service;

import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.api.refusal.MemberRefusal;
import dev.chojo.ember.api.refusal.Refusal;
import dev.chojo.ember.api.refusal.RefusalResponse;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.account.repository.AccountRepository;
import dev.chojo.ember.feature.account.service.AccountReach;
import dev.chojo.ember.feature.account.service.AuthService;
import dev.chojo.ember.feature.mail.service.MailRecipientService;
import dev.chojo.ember.feature.members.entity.StationMember;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MemberSetupMailServiceTest {
    private AccountRepository accounts;
    private MailRecipientService recipients;
    private AuthService auth;
    private MemberSetupMailService service;

    private static StationMember member(Integer accountId) {
        return new StationMember(7, 3, null, accountId, false, null, "Mara", StationUserType.MEMBER, null);
    }

    private static Account account(Instant setupCompletedAt) {
        return new Account(
                1, null, "mara@test.com", null, "Mara", "Nager", true, null, "Mara Nager", null, setupCompletedAt);
    }

    private Refusal refusalOf(StationMember member) {
        return assertThrows(RefusalResponse.class, () -> service.resend(member)).refusal();
    }

    @BeforeEach
    void setup() {
        accounts = mock(AccountRepository.class);
        recipients = mock(MailRecipientService.class);
        auth = mock(AuthService.class);
        service = new MemberSetupMailService(accounts, recipients, auth, mock(AccountReach.class));
    }

    @Test
    void aMemberWhoWaitsIsSentTheMailAgain() {
        when(accounts.findById(1)).thenReturn(Optional.of(account(null)));
        when(recipients.isReachable(1)).thenReturn(true);

        service.resend(member(1));

        verify(auth).sendPasswordSetup(1);
    }

    @Test
    void nobodyToSendItToIsRefused() {
        assertEquals(MemberRefusal.MEMBER_HAS_NO_ACCOUNT, refusalOf(member(null)));
        assertEquals(MemberRefusal.ACCOUNT_NOT_HERE_ON_SETUP_MAIL, refusalOf(member(1)));
        when(accounts.findById(1)).thenReturn(Optional.of(account(null)));
        assertEquals(MemberRefusal.ACCOUNT_NOBODY_TO_WRITE_TO, refusalOf(member(1)));
        verify(auth, never()).sendPasswordSetup(anyInt());
    }

    @Test
    void anAccountThatIsSetUpIsNotInvitedAgain() {
        when(accounts.findById(1)).thenReturn(Optional.of(account(Instant.EPOCH)));
        assertEquals(MemberRefusal.ACCOUNT_ALREADY_SET_UP, refusalOf(member(1)));

        when(accounts.findById(1)).thenReturn(Optional.of(account(null)));
        when(accounts.hasChosenPassword(1)).thenReturn(true);
        assertEquals(MemberRefusal.ACCOUNT_ALREADY_SET_UP, refusalOf(member(1)));
    }
}
