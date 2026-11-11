/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.station.service;

import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.account.service.AuthService;
import dev.chojo.ember.feature.mail.service.EmailService;
import dev.chojo.ember.feature.mail.service.MailLocaleService;
import dev.chojo.ember.feature.mail.service.MailRecipientService;
import dev.chojo.ember.feature.station.entity.ReminderKind;
import dev.chojo.ember.feature.station.entity.ReminderTrigger;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.entity.StationOverview;
import dev.chojo.ember.feature.station.entity.StationState;
import dev.chojo.ember.feature.station.repository.ManagerReminderRepository;
import dev.chojo.ember.feature.station.repository.StationOverviewRepository;
import dev.chojo.ember.lifecycle.Schedule;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The automatic reminders: once per trigger and waiting period however often the task runs, and never for
 * a station that moved on, a manager who cannot be written to, or one reminded by hand the day before.
 */
class ManagerReminderTaskTest extends RepositoryTestBase {
    private AuthService authService;
    private EmailService emailService;
    private ManagerReminderService service;
    private ManagerReminderTask task;

    @BeforeEach
    void setUp() {
        query("DELETE FROM station_manager_reminder;").single().delete();
        query("UPDATE station SET waiting_since = NULL;").single().update();
        authService = mock(AuthService.class);
        when(authService.sendPasswordSetup(anyInt())).thenReturn(true);
        when(authService.resendVerification(anyString())).thenReturn(true);
        emailService = mock(EmailService.class);
        service = new ManagerReminderService(
                new StationOverviewRepository(),
                new ManagerReminderRepository(),
                stationRepo,
                accountRepo,
                new MailRecipientService(accountRepo, stationMemberRepo),
                new MailLocaleService(accountRepo, applicationSettingRepo),
                authService,
                emailService,
                Clock.fixed(Instant.now(), ZoneOffset.UTC));
        task = new ManagerReminderTask(service);
    }

    @Test
    void eachReminderGoesOutOncePerWaitingPeriodHoweverOftenTheTaskRuns() {
        Station station = stationRepo.create("Eine Woche");
        Account manager = manager(station, "week", true);
        waitingFor(station, 8);

        assertEquals(1, task.sweep());
        assertEquals(0, task.sweep());
        verify(emailService, times(1))
                .sendStationSetupReminder(
                        eq(manager.email()),
                        anyString(),
                        eq(station.id()),
                        eq("Eine Woche"),
                        eq(station.uid()),
                        eq("de"));
        assertEquals(1, sentReminders(station, ReminderTrigger.AFTER_7_DAYS));
        assertEquals(0, reminders(station, ReminderTrigger.AFTER_21_DAYS));
    }

    @Test
    void aStationWaitingThreeWeeksIsSentOnlyTheLaterReminder() {
        Station station = stationRepo.create("Drei Wochen");
        Account manager = manager(station, "three", false);
        waitingFor(station, 25);

        assertEquals(1, task.sweep());
        assertEquals(0, task.sweep());

        verify(authService, times(1)).sendPasswordSetup(manager.id());
        assertEquals(1, reminders(station, ReminderTrigger.AFTER_21_DAYS));
        assertEquals(0, reminders(station, ReminderTrigger.AFTER_7_DAYS));
    }

    @Test
    void theFirstAndTheLastReminderEachGoOutOnceAndThenNothingMore() {
        Station station = stationRepo.create("Beide");
        manager(station, "both", true);
        waitingFor(station, 8);
        assertEquals(1, task.sweep());

        query(
                        "UPDATE station_manager_reminder SET waiting_since = waiting_since - INTERVAL '14 days', sent_at = sent_at - INTERVAL '14 days' WHERE station_id = :id;")
                .single(call().bind("id", station.id()))
                .update();
        query("UPDATE station SET waiting_since = waiting_since - INTERVAL '14 days' WHERE id = :id;")
                .single(call().bind("id", station.id()))
                .update();

        assertEquals(1, task.sweep());
        assertEquals(0, task.sweep());
        assertEquals(1, reminders(station, ReminderTrigger.AFTER_7_DAYS));
        assertEquals(1, reminders(station, ReminderTrigger.AFTER_21_DAYS));
    }

    @Test
    void aStationSetUpMeanwhileIsNotReminded() {
        Station station = stationRepo.create("Inzwischen fertig");
        manager(station, "done", true);
        waitingFor(station, 8);
        StationOverview due = service.dueFor(ReminderTrigger.AFTER_7_DAYS).stream()
                .filter(found -> found.id() == station.id())
                .findFirst()
                .orElseThrow();
        stationRepo.markSetupComplete(station.id());

        assertEquals(
                ManagerReminderService.Outcome.NOT_DUE,
                service.remindAutomatically(station.id(), ReminderTrigger.AFTER_7_DAYS, due.waitingSince()));
        assertEquals(0, reminders(station, ReminderTrigger.AFTER_7_DAYS));
        verify(emailService, never())
                .sendStationSetupReminder(
                        anyString(), anyString(), anyInt(), anyString(), eq(station.uid()), anyString());
    }

    @Test
    void aManagerNobodyCanWriteToIsPassedOverWithoutARecord() {
        Station station = stationRepo.create("Ohne Adresse");
        Account standIn = accountRepo.create(
                "standin-" + UUID.randomUUID() + Account.SYNTHETIC_EMAIL_SUFFIX, "Sam", "Stand", false);
        administrator(station, standIn);
        waitingFor(station, 8);

        assertEquals(0, task.sweep());
        assertEquals(0, task.sweep());

        assertEquals(0, reminders(station, ReminderTrigger.AFTER_7_DAYS));
        verify(authService, never()).sendPasswordSetup(anyInt());
    }

    @Test
    void aManagerWhoseAddressBouncedIsPassedOverWithoutARecord() {
        Station station = stationRepo.create("Abgewiesen");
        Account manager = manager(station, "bounced", true);
        emailQueueRepo.enqueue(manager.email(), "Betreff", "Inhalt");
        query("UPDATE email_queue SET status = 'SENT', delivery_status = 'HARD_BOUNCE' WHERE recipient = :to;")
                .single(call().bind("to", manager.email()))
                .update();
        waitingFor(station, 8);

        assertEquals(0, task.sweep());

        assertEquals(0, reminders(station, ReminderTrigger.AFTER_7_DAYS));
        verify(emailService, never())
                .sendStationSetupReminder(
                        anyString(), anyString(), anyInt(), anyString(), eq(station.uid()), anyString());
    }

    @Test
    void aReminderByHandTheDayBeforeSkipsTheAutomaticOneForGood() {
        Station station = stationRepo.create("Von Hand");
        manager(station, "byhand", true);
        waitingFor(station, 8);
        Account administrator = accountRepo.create("admin-" + UUID.randomUUID() + "@test.example", "A", "D", true);
        service.remindByHand(station.id(), administrator.id());

        assertEquals(0, task.sweep());
        assertEquals(0, task.sweep());

        assertEquals(1, reminders(station, ReminderTrigger.AFTER_7_DAYS), "written down as skipped");
        assertEquals(0, sentReminders(station, ReminderTrigger.AFTER_7_DAYS));
        verify(emailService, times(1))
                .sendStationSetupReminder(
                        anyString(), anyString(), anyInt(), anyString(), eq(station.uid()), anyString());
    }

    @Test
    void namingANewManagerStartsTheRemindersAfresh() {
        Station station = stationRepo.create("Neue Verwaltung");
        manager(station, "first", true);
        waitingFor(station, 8);
        assertEquals(1, task.sweep());

        stationRepo.startWaiting(station.id());
        assertEquals(0, task.sweep(), "the new period has only just begun");

        query("UPDATE station_manager_reminder SET sent_at = sent_at - INTERVAL '8 days' WHERE station_id = :id;")
                .single(call().bind("id", station.id()))
                .update();
        waitingFor(station, 8);
        assertEquals(1, task.sweep());
        assertEquals(2, reminders(station, ReminderTrigger.AFTER_7_DAYS));
    }

    @Test
    void anyReminderWithinADaySkipsTheAutomaticOneForGood() {
        Station station = stationRepo.create("Gerade erinnert");
        Account manager = manager(station, "recent", true);
        waitingFor(station, 22);
        new ManagerReminderRepository()
                .record(
                        station.id(),
                        manager.id(),
                        ReminderKind.SETUP_REMINDER,
                        ReminderTrigger.AFTER_7_DAYS,
                        Instant.now().minus(Duration.ofHours(2)),
                        null);

        assertEquals(0, task.sweep());
        assertEquals(0, task.sweep());

        assertEquals(1, reminders(station, ReminderTrigger.AFTER_21_DAYS), "written down as skipped");
        assertEquals(0, sentReminders(station, ReminderTrigger.AFTER_21_DAYS));
        verify(emailService, never())
                .sendStationSetupReminder(
                        anyString(), anyString(), anyInt(), anyString(), eq(station.uid()), anyString());
    }

    @Test
    void theLastReminderIsGivenUpFiveWeeksAfterTheWaitBegan() {
        Station station = stationRepo.create("Zu lange");
        manager(station, "toolong", true);
        waitingFor(station, 36);

        assertEquals(0, task.sweep());

        assertEquals(0, reminders(station, ReminderTrigger.AFTER_21_DAYS));
        assertEquals(0, reminders(station, ReminderTrigger.AFTER_7_DAYS));
    }

    @Test
    void aMailThatCouldNotBeQueuedLeavesNoRecordAndGoesOutOnTheNextRun() {
        Station station = stationRepo.create("Wackelig");
        manager(station, "flaky", true);
        waitingFor(station, 8);
        doThrow(new IllegalStateException("queue gone"))
                .doNothing()
                .when(emailService)
                .sendStationSetupReminder(
                        anyString(), anyString(), anyInt(), anyString(), eq(station.uid()), anyString());

        assertEquals(0, task.sweep());
        assertEquals(0, reminders(station, ReminderTrigger.AFTER_7_DAYS));

        assertEquals(1, task.sweep());
        assertEquals(1, sentReminders(station, ReminderTrigger.AFTER_7_DAYS));
    }

    @Test
    void aReminderAlreadyWrittenForTheWaitIsNotSentAgain() {
        Station station = stationRepo.create("Doppelt gefunden");
        manager(station, "twice", true);
        waitingFor(station, 8);
        StationOverview due = dueStation(station);

        assertEquals(
                ManagerReminderService.Outcome.SENT,
                service.remindAutomatically(station.id(), ReminderTrigger.AFTER_7_DAYS, due.waitingSince()));
        assertEquals(
                ManagerReminderService.Outcome.ALREADY_WRITTEN,
                service.remindAutomatically(station.id(), ReminderTrigger.AFTER_7_DAYS, due.waitingSince()));

        verify(emailService, times(1))
                .sendStationSetupReminder(
                        anyString(), anyString(), anyInt(), anyString(), eq(station.uid()), anyString());
    }

    @Test
    void aSetupLinkThatReachedNobodyIsPassedOverWithoutARecord() {
        Station station = stationRepo.create("Kein Link");
        manager(station, "nolink", false);
        waitingFor(station, 8);
        when(authService.sendPasswordSetup(anyInt())).thenReturn(false);

        assertEquals(0, task.sweep());

        assertEquals(0, reminders(station, ReminderTrigger.AFTER_7_DAYS));
    }

    @Test
    void anAddressCountedAsConfirmedIsWrittenDownAsNotSent() {
        Station station = stationRepo.create("Zählt als bestätigt");
        Account manager = manager(station, "granted", false);
        accountRepo.createCredential(manager.id(), "hash");
        waitingFor(station, 8);
        when(authService.resendVerification(anyString())).thenAnswer(invocation -> {
            accountRepo.setEmailVerified(manager.id());
            return true;
        });

        assertEquals(0, task.sweep());

        assertEquals(1, reminders(station, ReminderTrigger.AFTER_7_DAYS));
        assertEquals(0, sentReminders(station, ReminderTrigger.AFTER_7_DAYS));
    }

    @Test
    void theCopyOfAStationThatMovedAwayIsNeverRemindedAutomatically() {
        Station station = stationRepo.create("Weggezogen");
        manager(station, "moved", true);
        waitingFor(station, 8);
        StationOverview due = dueStation(station);
        stationRepo.markMovedAway(station.id(), null);

        assertTrue(
                service.dueFor(ReminderTrigger.AFTER_7_DAYS).stream().noneMatch(found -> found.id() == station.id()));
        assertEquals(
                ManagerReminderService.Outcome.NOT_DUE,
                service.remindAutomatically(station.id(), ReminderTrigger.AFTER_7_DAYS, due.waitingSince()));
        assertEquals(0, reminders(station, ReminderTrigger.AFTER_7_DAYS));
    }

    @Test
    void aManagerWhoCannotSignInGetsALinkAndOneWhoCanAReminder() {
        Station waiting = stationRepo.create("Ohne Anmeldung");
        Account newcomer = manager(waiting, "newcomer", false);
        Station settingUp = stationRepo.create("Mit Anmeldung");
        manager(settingUp, "veteran", true);
        waitingFor(waiting, 8);
        waitingFor(settingUp, 8);

        assertEquals(2, task.sweep());

        verify(authService).sendPasswordSetup(newcomer.id());
        assertEquals(1, remindersOfKind(waiting, ReminderKind.SETUP_LINK));
        assertEquals(1, remindersOfKind(settingUp, ReminderKind.SETUP_REMINDER));
    }

    @Test
    void theTaskLooksInEveryHour() {
        var declared = task.scheduledTasks();

        assertEquals(1, declared.size());
        assertEquals("manager-reminders", declared.getFirst().name());
        assertEquals(
                Schedule.fixedDelay(Duration.ofMinutes(25), Duration.ofHours(1)),
                declared.getFirst().schedule());
        declared.getFirst().work().run();
        assertTrue(service.dueFor(ReminderTrigger.MANUAL).isEmpty());

        var failing = mock(ManagerReminderService.class);
        when(failing.dueFor(any())).thenThrow(new IllegalStateException("database gone"));
        new ManagerReminderTask(failing).scheduledTasks().getFirst().work().run();
    }

    @Test
    void aFailureForOneStationLeavesTheOthersToGoOn() {
        var failing = mock(ManagerReminderService.class);
        var first = overview(1);
        var second = overview(2);
        when(failing.dueFor(ReminderTrigger.AFTER_7_DAYS)).thenReturn(List.of(first, second));
        when(failing.remindAutomatically(eq(1), any(), any())).thenThrow(new IllegalStateException("broken"));
        when(failing.remindAutomatically(eq(2), any(), any())).thenReturn(ManagerReminderService.Outcome.SENT);

        assertEquals(1, new ManagerReminderTask(failing).sweep());
    }

    private static StationOverview overview(int id) {
        return new StationOverview(
                id,
                UUID.randomUUID(),
                "Wache " + id,
                "de-DE",
                StationState.SETTING_UP,
                1,
                id,
                "Mara",
                "Nager",
                "mara@test.example",
                Instant.now(),
                null,
                null,
                null,
                null,
                false);
    }

    private static Account manager(Station station, String name, boolean canSignIn) {
        Account account =
                accountRepo.create(name + "-" + UUID.randomUUID() + "@test.example", "Mara", "Nager", canSignIn);
        if (canSignIn) accountRepo.createCredential(account.id(), "hash");
        administrator(station, account);
        return account;
    }

    private static void administrator(Station station, Account account) {
        var member = stationMemberRepo.create(station.id(), account.id());
        int permission = stationMemberRepo
                .findPermissionByName(StationPermission.STATION_ADMINISTRATOR)
                .orElseThrow()
                .id();
        stationMemberRepo.grantPermission(member.id(), permission);
    }

    private StationOverview dueStation(Station station) {
        return service.dueFor(ReminderTrigger.AFTER_7_DAYS).stream()
                .filter(found -> found.id() == station.id())
                .findFirst()
                .orElseThrow();
    }

    private static int remindersOfKind(Station station, ReminderKind kind) {
        return query("SELECT count(*) AS n FROM station_manager_reminder WHERE station_id = :id AND kind = :kind;")
                .single(call().bind("id", station.id()).bind("kind", kind))
                .map(row -> row.getInt("n"))
                .first()
                .orElseThrow();
    }

    private static void waitingFor(Station station, int days) {
        query("UPDATE station SET waiting_since = now() - make_interval(days => :days) WHERE id = :id;")
                .single(call().bind("days", days).bind("id", station.id()))
                .update();
    }

    private static int reminders(Station station, ReminderTrigger trigger) {
        return count(station, trigger, "TRUE");
    }

    private static int sentReminders(Station station, ReminderTrigger trigger) {
        return count(station, trigger, "sent_at IS NOT NULL");
    }

    private static int count(Station station, ReminderTrigger trigger, String condition) {
        return query("""
                SELECT count(*) AS n FROM station_manager_reminder
                WHERE station_id = :id AND trigger = :trigger AND %s;""", condition)
                .single(call().bind("id", station.id()).bind("trigger", trigger))
                .map(row -> row.getInt("n"))
                .first()
                .orElseThrow();
    }
}
