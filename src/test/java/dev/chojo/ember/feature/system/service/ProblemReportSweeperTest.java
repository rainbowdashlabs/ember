/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.system.service;

import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.system.repository.ProblemReportRepository;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What the sweep lets go of, and what it leaves alone.
 *
 * <p>Both halves matter. A sweep that keeps too much is the state this fixed, where reports were held
 * for good; a sweep that takes too much removes somebody's report while it is still theirs to answer.
 */
class ProblemReportSweeperTest extends RepositoryTestBase {

    private static Station station;
    private static Account account;
    private static StationMember member;

    private final ProblemReportRepository repository = new ProblemReportRepository();
    private final ProblemReportSweeper sweeper =
            new ProblemReportSweeper(repository, new ProblemReportScreenshotService(null));

    @BeforeAll
    static void setup() {
        station = stationRepo.create("SweeperStation");
        account = accountRepo.create("sweeper@test.com", "Sweep", "Reporter");
        member = stationMemberRepo.create(station.id(), account.id());
    }

    @AfterAll
    static void cleanup() {
        stationRepo.delete(station.id());
        accountRepo.delete(account.id());
    }

    private int aReport(String message) {
        return repository
                .create(station.id(), member.id(), "Sweep Reporter", message, null, null, null, null, null, null)
                .id();
    }

    /** Acknowledging stamps the moment, so a test that wants an old one has to age it by hand. */
    private static void dealtWithDaysAgo(int id, int days) {
        query("UPDATE problem_report SET acknowledged = TRUE, "
                        + "acknowledged_at = now() - CAST(:age AS interval) WHERE id = :id;")
                .single(call().bind("age", days + " days").bind("id", id))
                .update();
    }

    private boolean stillThere(int id) {
        return repository.findById(id).isPresent();
    }

    @Test
    void aReportDealtWithOverAMonthAgoIsLetGoOf() {
        int old = aReport("dealt with long ago");
        dealtWithDaysAgo(old, 31);

        sweeper.sweep();

        assertFalse(stillThere(old), "a month after it was dealt with, nothing is left to keep");
    }

    /** The month is what somebody comes back within, so it is not over until it is over. */
    @Test
    void aReportDealtWithThisMonthIsKept() {
        int recent = aReport("dealt with last week");
        dealtWithDaysAgo(recent, 7);

        sweeper.sweep();

        assertTrue(stillThere(recent), "it was dealt with a week ago, not a month");
    }

    /** Nothing is swept while it is still somebody's to answer, however long it has been sitting. */
    @Test
    void aReportNobodyHasDealtWithIsKept() {
        int open = aReport("nobody has looked at this");
        query("UPDATE problem_report SET created_at = now() - CAST('400 days' AS interval) WHERE id = :id;")
                .single(call().bind("id", open))
                .update();

        sweeper.sweep();

        assertTrue(stillThere(open), "age alone is not a reason to lose a report");
    }
}
