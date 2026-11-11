/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.station.service;

import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.refusal.MemberRefusal;
import dev.chojo.ember.api.refusal.Refusal;
import dev.chojo.ember.api.refusal.RefusalResponse;
import dev.chojo.ember.api.refusal.StationRefusal;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.account.service.AuthService;
import dev.chojo.ember.feature.mail.service.EmailService;
import dev.chojo.ember.feature.mail.service.MailLocaleService;
import dev.chojo.ember.feature.mail.service.MailRecipientService;
import dev.chojo.ember.feature.station.entity.ReminderAnswer;
import dev.chojo.ember.feature.station.entity.ReminderResult;
import dev.chojo.ember.feature.station.entity.ReminderTrigger;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.entity.StationOverview;
import dev.chojo.ember.feature.station.entity.StationState;
import dev.chojo.ember.feature.station.repository.ManagerReminderRepository;
import dev.chojo.ember.feature.station.repository.StationOverviewRepository;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Reminding a station's manager by hand: which mail goes out for which state, what is refused, and what is
 * written down.
 */
class ManagerReminderServiceTest extends RepositoryTestBase {
    private static final Instant NOW = Instant.now().truncatedTo(ChronoUnit.SECONDS);

    private AuthService authService;
    private EmailService emailService;
    private Account administrator;

    @BeforeEach
    void setUp() {
        authService = mock(AuthService.class);
        when(authService.sendPasswordSetup(anyInt())).thenReturn(true);
        when(authService.resendVerification(anyString())).thenReturn(true);
        emailService = mock(EmailService.class);
        administrator = account("admin", true);
    }

    @Test
    void aManagerWithoutAPasswordIsSentAFreshSetupLink() {
        Station station = stationRepo.create("Ohne Passwort");
        Account manager = manager(station, "nopass", false, false);

        ReminderAnswer answer = service(NOW).remindByHand(station.id(), administrator.id());
        StationOverview reminded = answer.station();

        assertEquals(ReminderResult.SENT, answer.result());
        verify(authService).sendPasswordSetup(manager.id());
        verify(authService, never()).resendVerification(anyString());
        verifyNoInteractions(emailService);
        assertEquals(NOW, reminded.lastReminderAt());
        assertEquals(ReminderTrigger.MANUAL, reminded.lastReminderTrigger());
        assertEquals(1, remindersBy(station, administrator));
    }

    @Test
    void aManagerWithAPasswordButAnUnconfirmedAddressIsSentTheConfirmationAgain() {
        Station station = stationRepo.create("Unbestätigt");
        Account manager = manager(station, "unconfirmed", false, true);

        service(NOW).remindByHand(station.id(), administrator.id());

        verify(authService).resendVerification(manager.email());
        verify(authService, never()).sendPasswordSetup(anyInt());
    }

    @Test
    void aManagerWhoCanSignInIsToldTheStationWaitsInTheStationsLanguage() {
        Station station = stationRepo.create("In Einrichtung");
        stationRepo.updateLocale(station.id(), "en-GB");
        Account manager = manager(station, "ready", true, true);

        service(NOW).remindByHand(station.id(), administrator.id());

        verify(emailService)
                .sendStationSetupReminder(
                        eq(manager.email()),
                        anyString(),
                        eq(station.id()),
                        eq("In Einrichtung"),
                        eq(station.uid()),
                        eq("en"));
        verifyNoInteractions(authService);
    }

    @Test
    void aSecondReminderByHandWithinTenMinutesIsRefused() {
        Station station = stationRepo.create("Doppelklick");
        manager(station, "double", false, false);
        service(NOW).remindByHand(station.id(), administrator.id());

        assertRefused(StationRefusal.MANAGER_REMINDED_JUST_NOW, () -> service(NOW.plus(Duration.ofMinutes(9)))
                .remindByHand(station.id(), administrator.id()));
        service(NOW.plus(Duration.ofMinutes(11))).remindByHand(station.id(), administrator.id());

        assertEquals(2, remindersBy(station, administrator));
    }

    @Test
    void aStationThatIsSetUpOrHasNoManagerIsRefused() {
        Station setUp = stationRepo.create("Fertig");
        manager(setUp, "done", true, true);
        stationRepo.markSetupComplete(setUp.id());
        Station noManager = stationRepo.create("Leer");

        assertRefused(
                StationRefusal.STATION_ALREADY_SET_UP, () -> service(NOW).remindByHand(setUp.id(), administrator.id()));
        assertRefused(StationRefusal.STATION_HAS_NO_MANAGER, () -> service(NOW)
                .remindByHand(noManager.id(), administrator.id()));
        assertRefused(StationRefusal.STATION_NOT_HERE_BY_NAME, () -> service(NOW)
                .remindByHand(Integer.MAX_VALUE, administrator.id()));
        verifyNoInteractions(authService, emailService);
    }

    @Test
    void aManagerNobodyCanWriteToIsRefusedAndNothingIsWritten() {
        Station station = stationRepo.create("Unerreichbar");
        Account standIn = accountRepo.create(
                "standin-" + UUID.randomUUID() + Account.SYNTHETIC_EMAIL_SUFFIX, "Sam", "Stand", false);
        accountRepo.createCredential(standIn.id(), "hash");
        administrator(station, standIn);

        assertRefused(MemberRefusal.ACCOUNT_NOBODY_TO_WRITE_TO, () -> service(NOW)
                .remindByHand(station.id(), administrator.id()));
        assertEquals(0, remindersBy(station, administrator));
        verifyNoInteractions(authService, emailService);
    }

    @Test
    void aManagerWhoFinishedTheirSetupWithoutAPasswordIsSentTheConfirmationNotASetupLink() {
        Station station = stationRepo.create("Mit Passkey");
        Account manager = manager(station, "passkey", false, false);
        accountRepo.markSetupCompleted(manager.id());

        service(NOW).remindByHand(station.id(), administrator.id());

        verify(authService).resendVerification(manager.email());
        verify(authService, never()).sendPasswordSetup(anyInt());
    }

    @Test
    void anAddressConfirmedMeanwhileIsRefusedAndNothingIsWritten() {
        Station station = stationRepo.create("Inzwischen bestätigt");
        manager(station, "meanwhile", false, true);
        when(authService.resendVerification(anyString())).thenReturn(false);

        assertRefused(MemberRefusal.ACCOUNT_ALREADY_SET_UP, () -> service(NOW)
                .remindByHand(station.id(), administrator.id()));
        assertEquals(0, remindersBy(station, administrator));
    }

    @Test
    void anAddressCountedAsConfirmedWithoutAMailIsNotRecordedAsSent() {
        Station station = stationRepo.create("Ohne Bestätigung");
        Account manager = manager(station, "granted", false, true);
        when(authService.resendVerification(anyString())).thenAnswer(invocation -> {
            accountRepo.setEmailVerified(manager.id());
            return true;
        });

        ReminderAnswer answer = service(NOW).remindByHand(station.id(), administrator.id());

        assertEquals(ReminderResult.CONFIRMED_WITHOUT_MAIL, answer.result());
        assertEquals(StationState.SETTING_UP, answer.station().state());
        assertEquals(0, remindersBy(station, administrator));
    }

    @Test
    void aSetupLinkThatReachedNobodyIsRefused() {
        Station station = stationRepo.create("Niemand erreicht");
        manager(station, "nobody", false, false);
        when(authService.sendPasswordSetup(anyInt())).thenReturn(false);

        assertRefused(MemberRefusal.ACCOUNT_NOBODY_TO_WRITE_TO, () -> service(NOW)
                .remindByHand(station.id(), administrator.id()));
        assertEquals(0, remindersBy(station, administrator));
    }

    @Test
    void theCopyOfAStationThatMovedAwayIsNotReminded() {
        Station station = stationRepo.create("Weggezogen");
        manager(station, "moved", false, false);
        stationRepo.markMovedAway(station.id(), null);

        assertRefused(
                StationRefusal.STATION_MOVED_AWAY, () -> service(NOW).remindByHand(station.id(), administrator.id()));
        verifyNoInteractions(authService, emailService);
    }

    @Test
    void aStationThatWaitedForNobodyStartsWaitingWithItsFirstReminder() {
        Station station = stationRepo.create("Ohne Wartezeit");
        manager(station, "idle", false, false);

        StationOverview reminded =
                service(NOW).remindByHand(station.id(), administrator.id()).station();

        assertNotNull(reminded.waitingSince());
        assertEquals(NOW, reminded.lastReminderAt(), "the reminder belongs to the wait it started");
    }

    @Test
    void aStationIsFoundByItsIdentifier() {
        Station station = stationRepo.create("Gesucht");

        assertTrue(service(NOW).find(station.uid()).isPresent());
        assertTrue(service(NOW).find(UUID.randomUUID()).isEmpty());
        assertTrue(service(NOW).list().stream().anyMatch(listed -> listed.id() == station.id()));
    }

    private ManagerReminderService service(Instant now) {
        return new ManagerReminderService(
                new StationOverviewRepository(),
                new ManagerReminderRepository(),
                stationRepo,
                accountRepo,
                new MailRecipientService(accountRepo, stationMemberRepo),
                new MailLocaleService(accountRepo, applicationSettingRepo),
                authService,
                emailService,
                Clock.fixed(now, ZoneOffset.UTC));
    }

    private static Account manager(Station station, String name, boolean confirmed, boolean chosePassword) {
        Account account = account(name, confirmed);
        if (chosePassword) accountRepo.createCredential(account.id(), "hash");
        administrator(station, account);
        return account;
    }

    private static Account account(String name, boolean confirmed) {
        return accountRepo.create(name + "-" + UUID.randomUUID() + "@test.example", "Mara", "Nager", confirmed);
    }

    private static void administrator(Station station, Account account) {
        var member = stationMemberRepo.create(station.id(), account.id());
        int permission = stationMemberRepo
                .findPermissionByName(StationPermission.STATION_ADMINISTRATOR)
                .orElseThrow()
                .id();
        stationMemberRepo.grantPermission(member.id(), permission);
    }

    private static int remindersBy(Station station, Account administrator) {
        return query("""
                SELECT count(*) AS n FROM station_manager_reminder
                WHERE station_id = :station_id AND sent_by = :sent_by AND trigger = 'MANUAL';""")
                .single(call().bind("station_id", station.id()).bind("sent_by", administrator.id()))
                .map(row -> row.getInt("n"))
                .first()
                .orElseThrow();
    }

    private static void assertRefused(Refusal refusal, Executable action) {
        var thrown = assertThrows(RefusalResponse.class, action);
        assertNotNull(thrown);
        assertEquals(refusal, thrown.refusal());
    }
}
