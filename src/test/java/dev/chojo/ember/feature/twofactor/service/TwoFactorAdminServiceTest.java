/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.twofactor.service;

import dev.chojo.ember.api.Refusal;
import dev.chojo.ember.api.RefusalResponse;
import dev.chojo.ember.api.auth.InstanceUserType;
import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.account.repository.AccountRepository;
import dev.chojo.ember.feature.account.repository.AccountRepository.PickerAccount;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import dev.chojo.ember.feature.twofactor.entity.TwoFactorAuditEntry;
import dev.chojo.ember.feature.twofactor.entity.TwoFactorEvent;
import dev.chojo.ember.feature.twofactor.entity.TwoFactorKind;
import dev.chojo.ember.feature.twofactor.repository.TwoFactorRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TwoFactorAdminServiceTest {
    private static final UUID UID = UUID.fromString("00000000-0000-0000-0000-0000000000aa");

    private TwoFactorRepository repository;
    private TwoFactorService twoFactor;
    private AccountRepository accounts;
    private StationMemberRepository members;
    private TwoFactorAdminService service;

    private static Account account(InstanceUserType type) {
        return new Account(42, null, "t@test.com", null, "Tom", "T", true, type, "Tom T", null, null);
    }

    private static PickerAccount picker() {
        return new PickerAccount(42, UID, "Tom T", "Tom", "T", "t@test.com");
    }

    private Refusal resetRefusal() {
        return assertThrows(RefusalResponse.class, () -> service.resetForStation(3, 42, 1, "ua", "DE"))
                .refusal();
    }

    @BeforeEach
    void setup() {
        repository = mock(TwoFactorRepository.class);
        twoFactor = mock(TwoFactorService.class);
        accounts = mock(AccountRepository.class);
        members = mock(StationMemberRepository.class);
        service = new TwoFactorAdminService(repository, twoFactor, accounts, members);
    }

    private void targetIsAtTheStation() {
        when(members.findByStationAndAccount(3, 42))
                .thenReturn(Optional.of(
                        new StationMember(9, 3, null, 42, false, null, "Tom", StationUserType.MEMBER, null)));
    }

    @Test
    void aMemberOfTheStationHasTheirSecondFactorCleared() {
        targetIsAtTheStation();
        when(accounts.findById(42)).thenReturn(Optional.of(account(InstanceUserType.USER)));
        when(twoFactor.resetAccount2FA(42, 1, "ua", "DE")).thenReturn(true);

        service.resetForStation(3, 42, 1, "ua", "DE");

        verify(twoFactor).resetAccount2FA(42, 1, "ua", "DE");
    }

    @Test
    void somebodyNotAtTheStationOrGoneIsNotYoursToReset() {
        assertEquals(Refusal.MEMBER_NOT_YOURS_TO_RESET, resetRefusal());
        targetIsAtTheStation();
        assertEquals(Refusal.MEMBER_NOT_YOURS_TO_RESET, resetRefusal());
        verify(twoFactor, never()).resetAccount2FA(anyInt(), any(), any(), any());
    }

    @Test
    void anAdministratorIsOnlyResetByAnAdministrator() {
        targetIsAtTheStation();
        when(accounts.findById(42)).thenReturn(Optional.of(account(InstanceUserType.ADMINISTRATOR)));

        assertEquals(Refusal.ADMIN_ONLY_RESET_BY_ADMIN, resetRefusal());
    }

    @Test
    void aResetThatDidNotHappenIsSaid() {
        targetIsAtTheStation();
        when(accounts.findById(42)).thenReturn(Optional.of(account(InstanceUserType.USER)));

        assertEquals(Refusal.SECOND_FACTOR_NOT_RESET_ON_STATION, resetRefusal());
    }

    @Test
    void accountsAreFoundByUidBeforeBySearch() {
        when(accounts.findPickerByUid(UID)).thenReturn(Optional.of(picker()));
        when(accounts.searchForPicker("tom", 50)).thenReturn(List.of(picker()));

        assertEquals(
                42, service.searchAccounts("x", UID.toString(), 20).getFirst().id());
        assertEquals("Tom", service.searchAccounts("tom", null, 500).getFirst().firstName());
        assertTrue(service.searchAccounts(null, "nope", 20).isEmpty());
        assertTrue(
                service.searchAccounts(null, UUID.randomUUID().toString(), 20).isEmpty());
    }

    @Test
    void theAuditLogIsPagedSanely() {
        var entry = new TwoFactorAuditEntry(1, 42, null, TwoFactorEvent.ENROLLED, null, "ua", "DE", Instant.EPOCH);
        when(repository.findRecentAudit(50, 0)).thenReturn(List.of(entry));
        when(repository.findAuditLog(42, 10, 5))
                .thenReturn(List.of(new TwoFactorAuditEntry(
                        2, 42, 1, TwoFactorEvent.REMOVED, TwoFactorKind.TOTP, "ua", "DE", Instant.EPOCH)));

        var everybody = service.audit(null, 500, -3).entries();
        var one = service.audit(42, 10, 5).entries();

        assertEquals(TwoFactorEvent.ENROLLED, everybody.getFirst().event());
        assertNull(everybody.getFirst().factorKind());
        assertEquals(TwoFactorKind.TOTP, one.getFirst().factorKind());
        assertEquals(1, service.audit(null, 0, 0).entries().size());
    }
}
