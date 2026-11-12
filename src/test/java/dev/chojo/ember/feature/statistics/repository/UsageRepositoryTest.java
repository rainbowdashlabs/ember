/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.statistics.repository;

import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.feature.attendance.entity.AttendanceEntry.AttendanceStatus;
import dev.chojo.ember.feature.attendance.entity.AttendanceEntry.EntrySource;
import dev.chojo.ember.feature.inventory.entity.InventoryItemMetadata;
import dev.chojo.ember.feature.inventory.entity.InventoryType;
import dev.chojo.ember.feature.statistics.entity.PeopleByType;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The daily usage row: one increment per account and day, a snapshot that leaves the live count
 * alone, and the people of the instance by member type.
 */
class UsageRepositoryTest extends RepositoryTestBase {
    private static UsageRepository usage;

    @BeforeAll
    static void init() {
        usage = new UsageRepository();
    }

    /** What the tests read back from today's row. */
    private record Today(int activeAccounts, int accounts, PeopleByType people) {}

    private static Today today() {
        return query("""
                        SELECT
                            active_accounts, accounts, people, people_trial, people_member, people_guardian,
                            people_team, people_manager, memberships
                        FROM instance_usage_daily
                        WHERE day = CURRENT_DATE;""")
                .single()
                .map(row -> new Today(
                        row.getInt("active_accounts"),
                        row.getInt("accounts"),
                        new PeopleByType(
                                row.getInt("people"),
                                row.getInt("people_trial"),
                                row.getInt("people_member"),
                                row.getInt("people_guardian"),
                                row.getInt("people_team"),
                                row.getInt("people_manager"),
                                row.getInt("memberships"))))
                .first()
                .orElse(new Today(0, 0, new PeopleByType(0, 0, 0, 0, 0, 0, 0)));
    }

    private static boolean lastActiveToday(int accountId) {
        return query("SELECT last_active_on = CURRENT_DATE AS today FROM account WHERE id = :id;")
                .single(call().bind("id", accountId))
                .map(row -> row.getBoolean("today"))
                .first()
                .orElseThrow();
    }

    private static int account(String name) {
        return accountRepo
                .create(name + "-" + UUID.randomUUID() + "@test.com", name, "Usage", true)
                .id();
    }

    private static int member(int stationId, int accountId, StationUserType type) {
        int id = stationMemberRepo.create(stationId, accountId).id();
        stationMemberRepo.setUserType(id, type);
        return id;
    }

    @Test
    void anAccountIsCountedOncePerDay() {
        int first = account("first");
        int second = account("second");
        int before = today().activeAccounts();

        assertTrue(usage.markActive(first));
        assertFalse(usage.markActive(first), "a second request the same day changes nothing");
        assertTrue(usage.markActive(second));

        assertEquals(before + 2, today().activeAccounts());
        assertTrue(lastActiveToday(first));
    }

    @Test
    void anAccountLastSeenOnAnEarlierDayIsCountedAgain() {
        int returning = account("returning");
        query("UPDATE account SET last_active_on = CURRENT_DATE - 1 WHERE id = :id;")
                .single(call().bind("id", returning))
                .update();
        int before = today().activeAccounts();

        assertTrue(usage.markActive(returning));

        assertEquals(before + 1, today().activeAccounts());
    }

    @Test
    void theSnapshotLeavesTheLiveCountAlone() {
        usage.markActive(account("before-snapshot"));
        int active = today().activeAccounts();

        usage.snapshot(LocalDate.now());
        account("after-first-snapshot");
        usage.snapshot(LocalDate.now());

        Today row = today();
        assertEquals(active, row.activeAccounts(), "the snapshot must not touch the people counted today");
        int accounts = query("SELECT count(*) FROM account;")
                .single()
                .map(r -> r.getInt(1))
                .first()
                .orElseThrow();
        assertEquals(accounts, row.accounts(), "the later snapshot replaces the earlier one");
    }

    @Test
    void peopleAreCountedOncePerTypeAcrossStations() {
        PeopleByType before = usage.peopleByType();
        int first = stationRepo.create("People First " + UUID.randomUUID()).id();
        int second = stationRepo.create("People Second " + UUID.randomUUID()).id();
        int home = stationRepo.create("People Home " + UUID.randomUUID()).id();
        stationRepo.markAsClusterHome(home);

        int inTwoStations = account("two-stations");
        member(first, inTwoStations, StationUserType.MEMBER);
        member(second, inTwoStations, StationUserType.TEAM);

        int guardian = account("guardian");
        member(first, guardian, StationUserType.GUARDIAN);
        stationMemberRepo.setFormer(member(second, guardian, StationUserType.TRIAL), true);

        int archived = account("archived");
        stationMemberRepo.setFormer(member(first, archived, StationUserType.MEMBER), true);

        int withoutAccount =
                stationMemberRepo.createWithoutAccount(first, "Ohne Konto").id();
        stationMemberRepo.setUserType(withoutAccount, StationUserType.TRIAL);

        member(home, account("association"), StationUserType.MANAGER);

        int moved = stationRepo.create("People Moved " + UUID.randomUUID()).id();
        member(moved, account("moved"), StationUserType.MANAGER);
        stationRepo.markMovedAway(moved, "https://elsewhere.example");

        PeopleByType after = usage.peopleByType();
        assertEquals(before.people() + 3, after.people());
        assertEquals(before.trial() + 1, after.trial(), "only the member without an account is on trial");
        assertEquals(before.member() + 1, after.member());
        assertEquals(before.guardian() + 1, after.guardian());
        assertEquals(before.team() + 1, after.team());
        assertEquals(
                before.manager(),
                after.manager(),
                "an association's home station holds nobody, and a station that moved away is not here");
        assertEquals(before.memberships() + 4, after.memberships());

        usage.snapshot(LocalDate.now());
        assertEquals(after, today().people(), "the snapshot keeps the figure the page shows");
    }

    private static int column(String name) {
        return query("SELECT %s AS value FROM instance_usage_daily WHERE day = CURRENT_DATE;", name)
                .single()
                .map(row -> row.getInt("value"))
                .first()
                .orElse(0);
    }

    private static int stationWithRecords(String name) {
        var station = stationRepo.create(name + " " + UUID.randomUUID());
        int member = member(station.id(), account(name), StationUserType.MEMBER);
        memberGroupRepo.create(station.id(), "Gruppe");
        var template = attendanceRepo.createTemplate(station.id(), "Dienst");
        Instant start = Instant.now().minus(Duration.ofHours(1));
        var sheet = attendanceRepo.createSession(
                template.id(), start, start.plus(Duration.ofMinutes(30)), null, "Dienst", null);
        attendanceRepo.createEntry(sheet.id(), member, AttendanceStatus.PRESENT, EntrySource.EXPECTED);
        var inventory = inventoryRepo.create(station.id(), "Funk", InventoryType.INTERNAL, false, false);
        inventoryRepo.createItem(inventory.id(), "F-1", "Funk", null, InventoryItemMetadata.empty());
        return station.id();
    }

    @Test
    void theSnapshotCountsOnlyTheRecordsOfStationsStillHere() {
        usage.snapshot(LocalDate.now());
        var figures = List.of("member_groups", "attendance_sessions", "attendance_entries", "inventory_items");
        Map<String, Integer> before = figures.stream().collect(Collectors.toMap(f -> f, UsageRepositoryTest::column));

        stationWithRecords("Counted");
        int home = stationWithRecords("Association Home");
        stationRepo.markAsClusterHome(home);
        int moved = stationWithRecords("Moved Away");
        stationRepo.markMovedAway(moved, null);
        usage.snapshot(LocalDate.now());

        for (String figure : figures) {
            assertEquals(before.get(figure) + 1, column(figure), figure + " counts the station still here only");
        }
    }

    @Test
    void yesterdaysMailsAreWrittenAgainWithTheLastHour() {
        LocalDate yesterday = LocalDate.now().minusDays(1);
        query("""
                        INSERT INTO instance_usage_daily (day, mails_sent) VALUES (CURRENT_DATE - 1, 2)
                        ON CONFLICT (day) DO UPDATE SET mails_sent = 2;""").single().update();
        query("""
                        INSERT INTO email_daily_count (day, count) VALUES (:day, 9)
                        ON CONFLICT (day) DO UPDATE SET count = 9;""").single(call().bind("day", yesterday)).update();

        usage.snapshot(LocalDate.now());

        int mails = query("SELECT mails_sent FROM instance_usage_daily WHERE day = CURRENT_DATE - 1;")
                .single()
                .map(row -> row.getInt("mails_sent"))
                .first()
                .orElseThrow();
        assertEquals(9, mails);
    }

    @Test
    void twoRequestsOfOneAccountAtOnceCountOnce() throws Exception {
        int account = account("at-once");
        int before = today().activeAccounts();
        var barrier = new CyclicBarrier(2);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<Boolean>> results = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                results.add(pool.submit(() -> {
                    barrier.await(10, TimeUnit.SECONDS);
                    return usage.markActive(account);
                }));
            }
            int counted = 0;
            for (var result : results) {
                if (result.get(30, TimeUnit.SECONDS)) counted++;
            }
            assertEquals(1, counted);
        } finally {
            pool.shutdownNow();
        }
        assertEquals(before + 1, today().activeAccounts());
    }
}
