/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.system.repository;

import dev.chojo.ember.feature.system.service.DatabaseLogAppender.LogLine;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApplicationLogRepositoryTest extends RepositoryTestBase {

    private final ApplicationLogRepository repository = new ApplicationLogRepository();

    private static LogLine line(String level, String logger, String message, Instant at) {
        return new LogLine(at, level, logger, "main", message, null);
    }

    @BeforeEach
    void clean() {
        repository.clear();
    }

    @Test
    void writtenLinesComeBackNewestFirst() {
        Instant now = Instant.now();
        repository.write(List.of(
                line("INFO", "dev.chojo.ember.First", "the older one", now.minusSeconds(60)),
                line("WARN", "dev.chojo.ember.Second", "the newer one", now)));

        var all = repository.search(List.of(), null, null, null, null, 10);

        assertEquals(2, all.size());
        assertEquals("the newer one", all.getFirst().message());
        assertNotNull(all.getFirst().loggedAt());
        assertEquals(2, repository.size());
    }

    @Test
    void searchLooksInTheMessageAndTheLoggerAndIgnoresCase() {
        Instant now = Instant.now();
        repository.write(List.of(
                line("INFO", "dev.chojo.ember.Mail", "sending failed", now),
                line("INFO", "dev.chojo.ember.Other", "nothing to see", now)));

        assertEquals(
                1, repository.search(List.of(), "SENDING", null, null, null, 10).size(), "the message, any case");
        assertEquals(
                1, repository.search(List.of(), "mail", null, null, null, 10).size(), "the logger name");
        assertEquals(2, repository.search(List.of(), null, null, null, null, 10).size(), "no term is no filter");
    }

    @Test
    void onlyTheWantedLevelsComeBack() {
        Instant now = Instant.now();
        repository.write(List.of(
                line("DEBUG", "dev.chojo.ember.A", "chatter", now),
                line("ERROR", "dev.chojo.ember.B", "trouble", now)));

        var errors = repository.search(List.of("ERROR"), null, null, null, null, 10);

        assertEquals(1, errors.size());
        assertEquals("trouble", errors.getFirst().message());
    }

    /**
     * A severity the log does not know is dropped rather than written into the statement, which is
     * what makes writing the known ones in safe.
     */
    @Test
    void anUnknownLevelIsIgnoredRatherThanTrusted() {
        repository.write(List.of(line("INFO", "dev.chojo.ember.A", "kept", Instant.now())));

        var all = repository.search(List.of("'); DROP TABLE application_log; --"), null, null, null, null, 10);

        assertEquals(1, all.size(), "the unusable filter is dropped, so everything comes back");
        assertEquals(1, repository.size(), "and the table is still there");
    }

    @Test
    void readingFurtherBackStartsBelowTheGivenLine() {
        Instant now = Instant.now();
        repository.write(List.of(
                line("INFO", "dev.chojo.ember.A", "one", now.minusSeconds(3)),
                line("INFO", "dev.chojo.ember.A", "two", now.minusSeconds(2)),
                line("INFO", "dev.chojo.ember.A", "three", now.minusSeconds(1))));

        var firstPage = repository.search(List.of(), null, null, null, null, 2);
        var nextPage = repository.search(
                List.of(), null, null, null, firstPage.getLast().id(), 2);

        assertEquals(2, firstPage.size());
        assertEquals(1, nextPage.size());
        assertEquals("one", nextPage.getFirst().message());
    }

    @Test
    void pruningRemovesOnlyWhatIsPastRetention() {
        Instant now = Instant.now();
        repository.write(List.of(
                line("INFO", "dev.chojo.ember.A", "ancient", now.minus(30, ChronoUnit.DAYS)),
                line("INFO", "dev.chojo.ember.A", "recent", now)));

        int removed = repository.prune(14);

        assertEquals(1, removed);
        var left = repository.search(List.of(), null, null, null, null, 10);
        assertEquals(1, left.size());
        assertEquals("recent", left.getFirst().message());
    }

    /**
     * A full drain from the appender is one batch, not five hundred statements. Whether it is
     * batched cannot be seen from here, but that a batch of this size arrives whole and in order
     * can, and that is what a loop would eventually get wrong.
     */
    @Test
    void awholeDrainIsWrittenAtOnce() {
        Instant now = Instant.now();
        var batch = new java.util.ArrayList<LogLine>();
        for (int i = 0; i < 500; i++) {
            batch.add(line("INFO", "dev.chojo.ember.Loud", "line " + i, now.plusMillis(i)));
        }

        repository.write(batch);

        assertEquals(500, repository.size());
        var newest = repository.search(List.of(), null, null, null, null, 1);
        assertEquals("line 499", newest.getFirst().message(), "and the last one written is the newest");
    }

    @Test
    void clearingEmptiesTheLog() {
        repository.write(List.of(line("INFO", "dev.chojo.ember.A", "something", Instant.now())));
        assertFalse(repository.search(List.of(), null, null, null, null, 10).isEmpty());

        repository.clear();

        assertTrue(repository.search(List.of(), null, null, null, null, 10).isEmpty());
        assertEquals(0, repository.size());
    }

    private static LogLine onThread(String logger, String thread, Instant at) {
        return new LogLine(at, "INFO", logger, thread, "a line", null);
    }

    @Test
    void threadsAreCountedWithoutTheirNumbering() {
        Instant now = Instant.now();
        repository.write(List.of(
                onThread("dev.chojo.ember.A", "pool-1", now),
                onThread("dev.chojo.ember.A", "pool-2", now),
                onThread("dev.chojo.ember.A", "pool-17", now),
                onThread("dev.chojo.ember.B", "main", now)));

        var threads = repository.threadFacets(List.of(), null, null, null, 10);

        assertEquals(2, threads.size(), "one entry per pool, not one per thread");
        assertEquals("pool-#", threads.getFirst().value());
        assertEquals(3, threads.getFirst().count());
    }

    @Test
    void narrowingToAThreadTakesTheNumberedOffName() {
        Instant now = Instant.now();
        repository.write(List.of(
                onThread("dev.chojo.ember.A", "pool-1", now),
                onThread("dev.chojo.ember.A", "pool-2", now),
                onThread("dev.chojo.ember.B", "main", now)));

        var pooled = repository.search(List.of(), null, null, "pool-#", null, 10);

        assertEquals(2, pooled.size(), "both threads of the pool, picked by the one name shown");
    }

    @Test
    void loggersAreCountedUnderTheFilterAndNotUnderThemselves() {
        Instant now = Instant.now();
        repository.write(List.of(
                line("ERROR", "dev.chojo.ember.Mail", "one", now),
                line("ERROR", "dev.chojo.ember.Mail", "two", now),
                line("INFO", "dev.chojo.ember.Mail", "three", now),
                line("ERROR", "dev.chojo.ember.Web", "four", now)));

        var errors = repository.loggerFacets(List.of("ERROR"), null, null, null, 10);

        assertEquals(2, errors.size(), "both loggers that carry an error");
        assertEquals("dev.chojo.ember.Mail", errors.getFirst().value());
        assertEquals(2, errors.getFirst().count(), "counted under the level filter, not over the whole log");
    }

    @Test
    void aChosenLoggerStillLeavesTheOthersToChooseFrom() {
        Instant now = Instant.now();
        repository.write(List.of(
                line("INFO", "dev.chojo.ember.Mail", "one", now), line("INFO", "dev.chojo.ember.Web", "two", now)));

        var loggers = repository.loggerFacets(List.of(), null, null, null, 10);

        assertEquals(2, loggers.size(), "a facet list that collapses to the chosen value cannot be changed");
    }

    /**
     * The list of loggers is cut off at a limit, so one that is quiet enough to fall below it can
     * only be reached by searching for it.
     */
    @Test
    void aLoggerBelowTheLimitIsFoundBySearchingForIt() {
        Instant now = Instant.now();
        repository.write(List.of(
                line("INFO", "dev.chojo.ember.Loud", "one", now),
                line("INFO", "dev.chojo.ember.Loud", "two", now),
                line("INFO", "dev.chojo.ember.Quiet", "three", now)));

        var topOne = repository.loggerFacets(List.of(), null, null, null, 1);
        var searched = repository.loggerFacets(List.of(), null, null, "quiet", 1);

        assertEquals("dev.chojo.ember.Loud", topOne.getFirst().value(), "the busiest fills the only place");
        assertEquals("dev.chojo.ember.Quiet", searched.getFirst().value(), "and the quiet one is reached by name");
    }

    @Test
    void aFacetValueCannotCarryAnInjection() {
        repository.write(List.of(line("INFO", "dev.chojo.ember.A", "something", Instant.now())));

        var found = repository.search(List.of(), null, "'); DROP TABLE application_log; --", null, null, 10);

        assertTrue(found.isEmpty(), "it matches no logger");
        assertEquals(1, repository.size(), "and the table is still there");
    }
}
