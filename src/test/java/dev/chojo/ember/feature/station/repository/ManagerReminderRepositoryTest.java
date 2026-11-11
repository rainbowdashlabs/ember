/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.station.repository;

import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.station.entity.ReminderKind;
import dev.chojo.ember.feature.station.entity.ReminderTrigger;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The record of reminders sent to station managers, and when a station starts waiting for one.
 */
class ManagerReminderRepositoryTest extends RepositoryTestBase {
    private final ManagerReminderRepository reminders = new ManagerReminderRepository();

    @Test
    void anAutomaticReminderIsWrittenOncePerWaitingPeriod() {
        Station station = stationRepo.create("Einmal erinnert");
        stationRepo.startWaiting(station.id());
        Account manager = account("once");

        assertTrue(record(station, manager, ReminderTrigger.AFTER_7_DAYS));
        assertFalse(record(station, manager, ReminderTrigger.AFTER_7_DAYS), "the same period again");
        assertTrue(record(station, manager, ReminderTrigger.AFTER_21_DAYS), "another trigger");

        query("UPDATE station SET waiting_since = waiting_since + INTERVAL '1 day' WHERE id = :id;")
                .single(call().bind("id", station.id()))
                .update();
        assertTrue(record(station, manager, ReminderTrigger.AFTER_7_DAYS), "a new waiting period");
    }

    @Test
    void remindersByHandAreWrittenEveryTime() {
        Station station = stationRepo.create("Oft erinnert");
        Account manager = account("often");
        Account administrator = account("admin");

        assertTrue(reminders.record(
                station.id(),
                manager.id(),
                ReminderKind.SETUP_REMINDER,
                ReminderTrigger.MANUAL,
                Instant.now(),
                administrator.id()));
        assertTrue(reminders.record(
                station.id(),
                manager.id(),
                ReminderKind.SETUP_REMINDER,
                ReminderTrigger.MANUAL,
                Instant.now(),
                administrator.id()));
        assertNotNull(waitingSinceOfNewestReminder(station), "a station that waited for nobody waits from now");
    }

    @Test
    void aReminderForAStationThatIsNotThereIsNotWritten() {
        assertFalse(reminders.record(
                Integer.MAX_VALUE,
                account("nowhere").id(),
                ReminderKind.SETUP_LINK,
                ReminderTrigger.MANUAL,
                Instant.now(),
                null));
        assertFalse(reminders.lockStation(Integer.MAX_VALUE));
    }

    @Test
    void onlyRemindersByHandThatWentOutCountAsRecent() {
        Station station = stationRepo.create("Kürzlich");
        Account manager = account("recent");
        Instant now = Instant.now();
        Instant since = now.minus(Duration.ofMinutes(10));

        assertFalse(reminders.sentByHandSince(station.id(), since));
        reminders.record(station.id(), manager.id(), ReminderKind.SETUP_LINK, ReminderTrigger.AFTER_7_DAYS, now, null);
        assertFalse(reminders.sentByHandSince(station.id(), since), "an automatic one does not count");
        reminders.record(
                station.id(),
                manager.id(),
                ReminderKind.SETUP_LINK,
                ReminderTrigger.MANUAL,
                now.minus(Duration.ofHours(1)),
                null);
        assertFalse(reminders.sentByHandSince(station.id(), since), "one before the moment does not count");
        reminders.record(station.id(), manager.id(), ReminderKind.SETUP_LINK, ReminderTrigger.MANUAL, now, null);
        assertTrue(reminders.sentByHandSince(station.id(), since));
        assertTrue(reminders.lockStation(station.id()));
    }

    @Test
    void anyReminderThatWentOutCountsAsRecentAndOneMarkedNotSentDoesNot() {
        Station station = stationRepo.create("Irgendwann erinnert");
        stationRepo.startWaiting(station.id());
        Account manager = account("any");
        Instant since = Instant.now().minus(Duration.ofHours(24));

        reminders.record(
                station.id(), manager.id(), ReminderKind.SETUP_LINK, ReminderTrigger.AFTER_7_DAYS, Instant.now(), null);
        assertTrue(reminders.sentSince(station.id(), since));

        assertTrue(reminders.markNotSent(station.id(), ReminderTrigger.AFTER_7_DAYS));
        assertFalse(reminders.sentSince(station.id(), since));
        assertFalse(reminders.markNotSent(station.id(), ReminderTrigger.AFTER_21_DAYS), "nothing written for it");
    }

    @Test
    void namingAManagerStartsTheWaitOnlyWhileTheStationIsNotSetUp() {
        Station waiting = stationRepo.create("Wartet");
        Station done = stationRepo.create("Fertig");
        stationRepo.markSetupComplete(done.id());

        assertTrue(stationRepo.startWaiting(waiting.id()));
        assertFalse(stationRepo.startWaiting(done.id()));
        assertTrue(waits(waiting));
        assertFalse(waits(done));
    }

    @Test
    void aStationWaitingForNobodyStartsWaitingOnceAndKeepsThatStart() {
        Station idle = stationRepo.create("Wartet noch nicht");
        Station done = stationRepo.create("Längst fertig");
        stationRepo.markSetupComplete(done.id());

        assertTrue(stationRepo.startWaitingIfIdle(idle.id()));
        assertFalse(stationRepo.startWaitingIfIdle(idle.id()), "a station already waiting keeps its start");
        assertFalse(stationRepo.startWaitingIfIdle(done.id()));
        assertTrue(waits(idle));
        assertFalse(waits(done));
    }

    @Test
    void anImportedStationWaitsOnlyWhenItHasAManagerAndIsNotSetUp() {
        Station withManager = stationRepo.create("Umgezogen mit Verwaltung");
        administrator(withManager, account("moved"));
        Station withoutManager = stationRepo.create("Umgezogen ohne Verwaltung");
        Station setUp = stationRepo.create("Umgezogen und eingerichtet");
        administrator(setUp, account("moved-done"));
        stationRepo.markSetupComplete(setUp.id());

        assertTrue(stationRepo.startWaitingAfterImport(withManager.id()));
        assertFalse(stationRepo.startWaitingAfterImport(withManager.id()), "a station already waiting keeps its wait");
        assertFalse(stationRepo.startWaitingAfterImport(withoutManager.id()));
        assertFalse(stationRepo.startWaitingAfterImport(setUp.id()));
        assertTrue(waits(withManager));
        assertFalse(waits(withoutManager));
    }

    @Test
    void onlyARefusalOnTheNewestMailToAnAddressCounts() {
        String address = "Bounce-" + UUID.randomUUID() + "@test.example";
        assertFalse(reminders.newestMailRefused(address), "nothing was ever sent there");

        emailQueueRepo.enqueue(address, "Erste", "Inhalt");
        delivered(address, "Erste", "HARD_BOUNCE");
        assertTrue(reminders.newestMailRefused(address.toLowerCase()), "compared without regard to case");

        emailQueueRepo.enqueue(address, "Zweite", "Inhalt");
        query(
                        "UPDATE email_queue SET created_at = created_at + INTERVAL '1 minute' WHERE subject = 'Zweite' AND recipient = :to;")
                .single(call().bind("to", address))
                .update();
        delivered(address, "Zweite", "DELIVERED");
        assertFalse(reminders.newestMailRefused(address), "a later mail arrived");
    }

    private static void delivered(String address, String subject, String status) {
        query(
                        "UPDATE email_queue SET status = 'SENT', delivery_status = :status WHERE recipient = :to AND subject = :subject;")
                .single(call().bind("status", status).bind("to", address).bind("subject", subject))
                .update();
    }

    private boolean record(Station station, Account manager, ReminderTrigger trigger) {
        return reminders.record(station.id(), manager.id(), ReminderKind.SETUP_LINK, trigger, Instant.now(), null);
    }

    private static Account account(String name) {
        return accountRepo.create(name + "-" + UUID.randomUUID() + "@test.example", "Mara", "Nager", true);
    }

    private static void administrator(Station station, Account account) {
        var member = stationMemberRepo.create(station.id(), account.id());
        int permission = stationMemberRepo
                .findPermissionByName(StationPermission.STATION_ADMINISTRATOR)
                .orElseThrow()
                .id();
        stationMemberRepo.grantPermission(member.id(), permission);
    }

    private static boolean waits(Station station) {
        return query("SELECT waiting_since IS NOT NULL AS waits FROM station WHERE id = :id;")
                .single(call().bind("id", station.id()))
                .map(row -> row.getBoolean("waits"))
                .first()
                .orElseThrow();
    }

    private static Instant waitingSinceOfNewestReminder(Station station) {
        return query(
                        "SELECT waiting_since FROM station_manager_reminder WHERE station_id = :id ORDER BY id DESC LIMIT 1;")
                .single(call().bind("id", station.id()))
                .map(row -> row.get("waiting_since", INSTANT_TIMESTAMP))
                .first()
                .orElse(null);
    }
}
