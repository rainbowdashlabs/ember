/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.station.repository;

import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.station.entity.ReminderKind;
import dev.chojo.ember.feature.station.entity.ReminderTrigger;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.entity.StationOverview;
import dev.chojo.ember.feature.station.entity.StationState;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The station list of the instance administration: one row per station somebody runs, its state derived
 * from its manager and its setup.
 */
class StationOverviewRepositoryTest extends RepositoryTestBase {
    private final StationOverviewRepository overviews = new StationOverviewRepository();
    private final ManagerReminderRepository reminders = new ManagerReminderRepository();

    @Test
    void aStationWithoutAnAdministratorWithAnAccountHasNoManager() {
        Station station = stationRepo.create("Ohne Verwaltung");
        StationMember withoutAccount = stationMemberRepo.createWithoutAccount(station.id(), "Ohne Konto");
        grantAdministrator(withoutAccount.id());
        Account plain = accountRepo.create(unique("plain"), "Paul", "Plain", true);
        stationMemberRepo.create(station.id(), plain.id());

        StationOverview overview = overviews.find(station.id()).orElseThrow();

        assertEquals(StationState.NO_MANAGER, overview.state());
        assertNull(overview.managerAccountId());
        assertEquals(2, overview.memberCount());
        assertNotNull(overview.createdAt(), "a station made now knows when it was made");
    }

    @Test
    void aManagerWithoutAChosenPasswordIsWaitedFor() {
        Station station = stationRepo.create("Ohne Passwort");
        Account manager = manager(station, "nopass", true, false);

        StationOverview overview = overviews.find(station.id()).orElseThrow();

        assertEquals(StationState.WAITING_FOR_MANAGER, overview.state());
        assertEquals(manager.id(), overview.managerAccountId());
        assertEquals("Mara", overview.managerFirstName());
        assertEquals(manager.email(), overview.managerEmail());
    }

    @Test
    void aManagerWithAnUnconfirmedAddressIsWaitedFor() {
        Station station = stationRepo.create("Unbestätigt");
        manager(station, "unconfirmed", false, true);

        assertEquals(
                StationState.WAITING_FOR_MANAGER,
                overviews.find(station.id()).orElseThrow().state());
    }

    @Test
    void aManagerWhoMustStillChangeTheirPasswordIsWaitedFor() {
        Station station = stationRepo.create("Erzwungen");
        Account manager = manager(station, "forced", true, true);
        accountRepo.setForcePasswordChange(manager.id(), true);

        assertEquals(
                StationState.WAITING_FOR_MANAGER,
                overviews.find(station.id()).orElseThrow().state());
    }

    @Test
    void aManagerWhoCanSignInIsSettingTheStationUp() {
        Station station = stationRepo.create("In Einrichtung");
        manager(station, "ready", true, true);

        assertEquals(
                StationState.SETTING_UP,
                overviews.find(station.id()).orElseThrow().state());
    }

    @Test
    void aFinishedSetupIsSetUpWhoeverRunsTheStation() {
        Station station = stationRepo.create("Eingerichtet");
        stationRepo.markSetupComplete(station.id());

        assertEquals(
                StationState.SET_UP, overviews.find(station.id()).orElseThrow().state());
    }

    @Test
    void theOwnerIsTheManagerBeforeAnyOtherAdministrator() {
        Station station = stationRepo.create("Zwei Verwaltungen");
        Account first = manager(station, "first", true, true);
        Account owner = manager(station, "owner", true, false);
        StationMember ownerMember = stationMemberRepo.findByStation(station.id()).stream()
                .filter(member -> Integer.valueOf(owner.id()).equals(member.accountId()))
                .findFirst()
                .orElseThrow();
        stationRepo.setOwner(station.id(), ownerMember.id());

        StationOverview overview = overviews.find(station.id()).orElseThrow();

        assertEquals(owner.id(), overview.managerAccountId(), "the owner, not " + first.id());
        assertEquals(StationState.WAITING_FOR_MANAGER, overview.state());
    }

    @Test
    void anAssociationHomeIsNeverListed() {
        Station home = stationRepo.create("Verbundheim");
        stationRepo.markAsClusterHome(home.id());
        Station member = stationRepo.create("Verbundwache");
        var cluster = clusterRepo.create("Verbund Nord", null, home.id());
        stationRepo.setCluster(member.id(), cluster.id());

        assertTrue(overviews.find(home.id()).isEmpty());
        assertTrue(overviews.list().stream().noneMatch(station -> station.id() == home.id()));
        assertEquals("Verbund Nord", overviews.find(member.id()).orElseThrow().associationName());
    }

    @Test
    void theListHoldsEveryStationInTheOrderTheyWereMade() {
        Station earlier = stationRepo.create("Früher");
        Station later = stationRepo.create("Später");

        var ids = overviews.list().stream().map(StationOverview::id).toList();

        assertTrue(ids.indexOf(earlier.id()) < ids.indexOf(later.id()));
        assertTrue(overviews.find(Integer.MAX_VALUE).isEmpty());
    }

    @Test
    void theLastReminderIsTheNewestOneThatWentOut() {
        Station station = stationRepo.create("Erinnert");
        Account manager = manager(station, "reminded", true, false);
        stationRepo.startWaiting(station.id());
        Instant earlier = Instant.now().minus(2, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
        Instant later = Instant.now().minus(1, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
        reminders.record(station.id(), manager.id(), ReminderKind.SETUP_LINK, ReminderTrigger.MANUAL, earlier, null);
        reminders.record(
                station.id(), manager.id(), ReminderKind.SETUP_LINK, ReminderTrigger.AFTER_7_DAYS, later, null);
        reminders.record(
                station.id(), manager.id(), ReminderKind.SETUP_LINK, ReminderTrigger.AFTER_21_DAYS, null, null);

        StationOverview overview = overviews.find(station.id()).orElseThrow();

        assertEquals(later, overview.lastReminderAt());
        assertEquals(ReminderTrigger.AFTER_7_DAYS, overview.lastReminderTrigger());
    }

    @Test
    void aStationNobodyRemindedHasNoLastReminder() {
        Station station = stationRepo.create("Nie erinnert");

        StationOverview overview = overviews.find(station.id()).orElseThrow();

        assertNull(overview.lastReminderAt());
        assertNull(overview.lastReminderTrigger());
        assertFalse(overview.state().waitsForManager());
        assertFalse(overview.movedAway());
    }

    @Test
    void aManagerWhoFinishedTheirSetupWithoutAPasswordIsSettingUpOnceConfirmed() {
        Station confirmed = stationRepo.create("Mit Passkey");
        Account passkey = manager(confirmed, "passkey", true, false);
        accountRepo.markSetupCompleted(passkey.id());
        Station unconfirmed = stationRepo.create("Mit Passkey, unbestätigt");
        Account pending = manager(unconfirmed, "passkey-pending", false, false);
        accountRepo.markSetupCompleted(pending.id());

        assertEquals(
                StationState.SETTING_UP,
                overviews.find(confirmed.id()).orElseThrow().state());
        assertEquals(
                StationState.WAITING_FOR_MANAGER,
                overviews.find(unconfirmed.id()).orElseThrow().state());
    }

    @Test
    void archivedMembersAreNeitherManagerNorCounted() {
        Station station = stationRepo.create("Mit Ehemaligen");
        manager(station, "former-admin", true, true);
        Account member = accountRepo.create(unique("former-member"), "Fritz", "Früher", true);
        stationMemberRepo.create(station.id(), member.id());
        for (StationMember listed : stationMemberRepo.findByStation(station.id())) {
            stationMemberRepo.setFormer(listed.id(), true);
        }

        StationOverview overview = overviews.find(station.id()).orElseThrow();

        assertEquals(StationState.NO_MANAGER, overview.state());
        assertNull(overview.managerAccountId());
        assertEquals(0, overview.memberCount());
    }

    @Test
    void aStationThatIsSetUpWaitsForNobody() {
        Station station = stationRepo.create("Fertig gewartet");
        stationRepo.startWaiting(station.id());
        stationRepo.markSetupComplete(station.id());

        assertNull(overviews.find(station.id()).orElseThrow().waitingSince());
    }

    @Test
    void aReminderFromAnEarlierWaitIsNotTheLastReminder() {
        Station station = stationRepo.create("Neue Wartezeit");
        Account manager = manager(station, "earlier", true, false);
        stationRepo.startWaiting(station.id());
        reminders.record(
                station.id(), manager.id(), ReminderKind.SETUP_LINK, ReminderTrigger.MANUAL, Instant.now(), null);
        query("UPDATE station SET waiting_since = waiting_since + INTERVAL '1 hour' WHERE id = :id;")
                .single(call().bind("id", station.id()))
                .update();

        assertNull(overviews.find(station.id()).orElseThrow().lastReminderAt());
    }

    /**
     * The overview's count of waiting stations is the list's "waiting" filter: a manager who cannot sign
     * in yet and one setting up count, a station without a manager, one that is set up and the copy of one
     * that moved away do not.
     */
    @Test
    void theWaitingCountHoldsTheStationsTheWaitingFilterShows() {
        int before = overviews.countWaiting();
        manager(stationRepo.create("Zaehlt Wartend"), "countwait", true, false);
        manager(stationRepo.create("Zaehlt Einrichtung"), "countsetup", true, true);
        stationRepo.create("Zaehlt Ohne Verwaltung");
        Station moved = stationRepo.create("Zaehlt Weggezogen");
        manager(moved, "countmoved", true, false);
        stationRepo.markMovedAway(moved.id(), "https://anderswo.example");

        assertEquals(before + 2, overviews.countWaiting());
    }

    @Test
    void theCopyAStationLeftWhenItMovedAwayIsMarked() {
        Station moved = stationRepo.create("Weggezogen");
        stationRepo.markMovedAway(moved.id(), "https://anderswo.example");

        assertTrue(overviews.find(moved.id()).orElseThrow().movedAway());
    }

    @Test
    void anAutomaticReminderIsDueForAWaitingStationWithinItsWindowUntilItIsWritten() {
        Station waiting = stationRepo.create("Wartet acht Tage");
        Account manager = manager(waiting, "due", true, true);
        Station nobody = stationRepo.create("Wartet ohne Verwaltung");
        query("UPDATE station SET waiting_since = now() - INTERVAL '8 days' WHERE id IN (:a, :b);")
                .single(call().bind("a", waiting.id()).bind("b", nobody.id()))
                .update();
        Instant now = Instant.now();

        assertTrue(isDue(
                ReminderTrigger.AFTER_7_DAYS, now.minus(7, ChronoUnit.DAYS), now.minus(21, ChronoUnit.DAYS), waiting));
        assertFalse(
                isDue(ReminderTrigger.AFTER_7_DAYS, now.minus(7, ChronoUnit.DAYS), null, nobody), "nobody to remind");
        assertFalse(isDue(ReminderTrigger.AFTER_21_DAYS, now.minus(21, ChronoUnit.DAYS), null, waiting), "too early");
        assertFalse(
                isDue(
                        ReminderTrigger.AFTER_7_DAYS,
                        now.minus(7, ChronoUnit.DAYS),
                        now.minus(1, ChronoUnit.DAYS),
                        waiting),
                "waited past the window");

        reminders.record(
                waiting.id(), manager.id(), ReminderKind.SETUP_REMINDER, ReminderTrigger.AFTER_7_DAYS, now, null);
        assertFalse(
                isDue(ReminderTrigger.AFTER_7_DAYS, now.minus(7, ChronoUnit.DAYS), null, waiting), "already written");
    }

    private boolean isDue(ReminderTrigger trigger, Instant waitedAt, Instant notBefore, Station station) {
        return overviews.dueForReminder(trigger, waitedAt, notBefore).stream()
                .anyMatch(due -> due.id() == station.id());
    }

    private Account manager(Station station, String name, boolean confirmed, boolean chosePassword) {
        Account account = accountRepo.create(unique(name), "Mara", "Nager", confirmed);
        if (chosePassword) accountRepo.createCredential(account.id(), "hash");
        StationMember member = stationMemberRepo.create(station.id(), account.id());
        grantAdministrator(member.id());
        return account;
    }

    private static void grantAdministrator(int memberId) {
        int permission = stationMemberRepo
                .findPermissionByName(StationPermission.STATION_ADMINISTRATOR)
                .orElseThrow()
                .id();
        stationMemberRepo.grantPermission(memberId, permission);
    }

    private static String unique(String name) {
        return name + "-" + UUID.randomUUID() + "@test.example";
    }
}
