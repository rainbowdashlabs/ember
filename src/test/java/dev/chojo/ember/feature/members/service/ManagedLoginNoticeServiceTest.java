/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.service;

import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.conf.file.elements.Auth;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.account.service.AuthService;
import dev.chojo.ember.feature.mail.service.EmailService;
import dev.chojo.ember.feature.mail.service.MailLocaleService;
import dev.chojo.ember.feature.mail.service.MailRecipientService;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.repository.ManagedLoginNoticeRepository;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.system.repository.ApplicationSettingRepository;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;

/**
 * What a member is told, once the wait after their guardian's switch is over. The state is read
 * again at that moment, so what is sent describes the member as they are then.
 */
class ManagedLoginNoticeServiceTest extends RepositoryTestBase {

    private static ManagedLoginNoticeRepository noticeRepo;
    private static ManagedLoginNoticeService service;
    private static AuthService authService;
    private static EmailService emailService;
    private static Station station;
    private static Account childAccount;
    private static StationMember child;
    private static int loginPermissionId;

    @BeforeAll
    static void setup() {
        noticeRepo = new ManagedLoginNoticeRepository();
        authService = mock(AuthService.class);
        emailService = mock(EmailService.class);
        service = new ManagedLoginNoticeService(
                noticeRepo,
                stationMemberRepo,
                accountRepo,
                stationRepo,
                new MailLocaleService(accountRepo, new ApplicationSettingRepository()),
                new MailRecipientService(accountRepo, stationMemberRepo),
                authService,
                emailService,
                new Auth());

        station = stationRepo.create("Notice Station");
        childAccount = accountRepo.create("lena@notice.example.org", "Lena", "Sommer");
        child = stationMemberRepo.create(station.id(), childAccount.id());
        loginPermissionId = stationMemberRepo
                .findPermissionByName(StationPermission.LOGIN)
                .orElseThrow()
                .id();
    }

    @AfterAll
    static void cleanup() {
        stationRepo.delete(station.id());
        accountRepo.delete(childAccount.id());
    }

    @BeforeEach
    void resetState() {
        reset(authService, emailService);
        noticeRepo.cancel(child.id());
        stationMemberRepo.revokePermission(child.id(), loginPermissionId);
        accountRepo.deleteCredential(childAccount.id());
        accountRepo.updateEmail(childAccount.id(), "lena@notice.example.org");
    }

    private void due(boolean granted) {
        noticeRepo.schedule(child.id(), granted, Instant.now().minus(1, ChronoUnit.MINUTES));
    }

    @Test
    void anAccountNobodyHasClaimedIsSentTheSetupMail() {
        stationMemberRepo.grantPermission(child.id(), loginPermissionId);
        due(true);

        service.dispatch();

        verify(authService).sendPasswordSetup(childAccount.id());
        verify(emailService, never()).sendManagedLoginGrantedNotice(anyString(), anyString(), anyString(), anyString());
        assertTrue(noticeRepo.find(child.id()).isEmpty());
    }

    @Test
    void anAccountThatIsSetUpIsToldItMaySignIn() {
        accountRepo.createCredential(childAccount.id(), "hash");
        stationMemberRepo.grantPermission(child.id(), loginPermissionId);
        due(true);

        service.dispatch();

        verify(emailService)
                .sendManagedLoginGrantedNotice(
                        eq("lena@notice.example.org"), eq("Lena"), eq("Notice Station"), anyString());
        verify(authService, never()).sendPasswordSetup(anyInt());
    }

    @Test
    void anAccountThatIsSetUpIsToldItMayNoLongerSignIn() {
        accountRepo.createCredential(childAccount.id(), "hash");
        due(false);

        service.dispatch();

        verify(emailService)
                .sendManagedLoginRevokedNotice(
                        eq("lena@notice.example.org"), eq("Lena"), eq("Notice Station"), anyString());
    }

    @Test
    void anAccountNobodyClaimedIsNotToldOfALossItNeverHad() {
        due(false);

        service.dispatch();

        verify(emailService, never()).sendManagedLoginRevokedNotice(anyString(), anyString(), anyString(), anyString());
        verify(authService, never()).sendPasswordSetup(anyInt());
        assertTrue(noticeRepo.find(child.id()).isEmpty());
    }

    @Test
    void anAddressNothingCanBeDeliveredToIsNotWrittenTo() {
        accountRepo.createCredential(childAccount.id(), "hash");
        accountRepo.updateEmail(childAccount.id(), "lena@notice.local");
        stationMemberRepo.grantPermission(child.id(), loginPermissionId);
        due(true);

        service.dispatch();

        verify(emailService, never()).sendManagedLoginGrantedNotice(anyString(), anyString(), anyString(), anyString());
        assertTrue(noticeRepo.find(child.id()).isEmpty());
    }

    @Test
    void whatIsSentFollowsTheStateAtTheEndOfTheWaitNotTheStart() {
        accountRepo.createCredential(childAccount.id(), "hash");
        due(true);

        service.dispatch();

        verify(emailService)
                .sendManagedLoginRevokedNotice(
                        eq("lena@notice.example.org"), eq("Lena"), eq("Notice Station"), anyString());
        verify(emailService, never()).sendManagedLoginGrantedNotice(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void aChangeWhoseWaitIsNotOverStaysWhereItIs() {
        accountRepo.createCredential(childAccount.id(), "hash");
        stationMemberRepo.grantPermission(child.id(), loginPermissionId);
        noticeRepo.schedule(child.id(), true, Instant.now().plus(5, ChronoUnit.MINUTES));

        service.dispatch();

        verify(emailService, never()).sendManagedLoginGrantedNotice(anyString(), anyString(), anyString(), anyString());
        assertTrue(noticeRepo.find(child.id()).isPresent());
    }
}
