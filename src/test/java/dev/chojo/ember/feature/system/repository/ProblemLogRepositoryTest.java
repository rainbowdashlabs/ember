/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.system.repository;

import dev.chojo.ember.feature.system.entity.ProblemLevel;
import dev.chojo.ember.feature.system.entity.ProblemOccurrence;
import dev.chojo.ember.feature.system.entity.ProblemRecord;
import dev.chojo.ember.feature.system.entity.ProblemState;
import dev.chojo.ember.feature.system.entity.StoredProblem;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The problem log as rows: one row per problem that adds up what was gathered, keeps its last
 * occurrences, reopens when a resolved problem comes back, and goes once its retention passed.
 */
class ProblemLogRepositoryTest extends RepositoryTestBase {
    private final ProblemLogRepository problems = new ProblemLogRepository();

    private static ProblemRecord record(String fingerprint, ProblemLevel level, List<String> messages, Instant at) {
        return new ProblemRecord(
                fingerprint,
                level,
                "dev.chojo.ember.Test",
                messages.getFirst(),
                "java.lang.IllegalStateException",
                "broken",
                "java.lang.IllegalStateException: broken",
                messages,
                messages.size(),
                at,
                at,
                List.of(new ProblemOccurrence(at, "worker-1")));
    }

    private static String fingerprint() {
        return "fp-" + UUID.randomUUID();
    }

    @Test
    void aNewProblemIsOpenAndTheSameOneAddsUp() {
        String fp = fingerprint();
        Instant first = Instant.now().minus(1, ChronoUnit.HOURS).truncatedTo(ChronoUnit.MILLIS);
        Instant later = first.plus(10, ChronoUnit.MINUTES);

        var created = problems.record(record(fp, ProblemLevel.ERROR, List.of("one", "two"), first));
        var added = problems.record(record(fp, ProblemLevel.ERROR, List.of("two", "three"), later));

        assertNull(created.previous());
        assertTrue(created.newOrReopened());
        assertEquals(created.id(), added.id());
        assertFalse(added.newOrReopened(), "an open problem happening again is not news");
        var stored = problems.find(created.id()).orElseThrow();
        assertEquals(ProblemState.OPEN, stored.state());
        assertEquals(4, stored.count());
        assertEquals(List.of("one", "two", "three"), stored.distinctMessages(), "new messages join in order, once");
        assertEquals(first, stored.firstSeen());
        assertEquals(later, stored.lastSeen());
        assertEquals("one", stored.message(), "the message stays the first one");
    }

    @Test
    void theMessagesStopAtTheirLimit() {
        String fp = fingerprint();
        List<String> many =
                new ArrayList<>(IntStream.range(0, 30).mapToObj(i -> "m" + i).toList());

        var created = problems.record(record(fp, ProblemLevel.WARN, many.subList(0, 15), Instant.now()));
        problems.record(record(fp, ProblemLevel.WARN, many.subList(15, 30), Instant.now()));

        assertEquals(
                many.subList(0, ProblemLogRepository.MESSAGE_LIMIT),
                problems.find(created.id()).orElseThrow().distinctMessages(),
                "the first twenty, in the order they came");
    }

    @Test
    void aFirstRecordWithMoreMessagesThanTheLimitIsCutAndRepeatsAreDropped() {
        List<String> many =
                new ArrayList<>(IntStream.range(0, 25).mapToObj(i -> "n" + i).toList());
        many.add(1, "n0");

        var created = problems.record(record(fingerprint(), ProblemLevel.WARN, many, Instant.now()));

        assertEquals(
                IntStream.range(0, ProblemLogRepository.MESSAGE_LIMIT)
                        .mapToObj(i -> "n" + i)
                        .toList(),
                problems.find(created.id()).orElseThrow().distinctMessages());
    }

    @Test
    void aWarningWithoutExceptionKeepsTheLatestExceptionTogetherWithItsStacktrace() {
        String fp = fingerprint();
        Instant at = Instant.now();
        var bare = new ProblemRecord(
                fp,
                ProblemLevel.WARN,
                "dev.chojo.ember.Test",
                "plain",
                null,
                null,
                null,
                List.of("plain"),
                1,
                at,
                at,
                List.of());
        var created = problems.record(bare);
        var stored = problems.find(created.id()).orElseThrow();
        assertNull(stored.exceptionClass());
        assertNull(stored.stacktrace());
        assertTrue(problems.occurrences(created.id()).isEmpty(), "a record without occurrences writes none");

        var withTrace = new ProblemRecord(
                fp,
                ProblemLevel.WARN,
                "dev.chojo.ember.Test",
                "plain",
                "java.lang.IllegalStateException",
                "first",
                "trace one",
                List.of("plain"),
                1,
                at,
                at,
                List.of());
        var withoutMessage = new ProblemRecord(
                fp,
                ProblemLevel.WARN,
                "dev.chojo.ember.Test",
                "plain",
                "java.lang.IllegalStateException",
                null,
                "trace two",
                List.of("plain"),
                1,
                at,
                at,
                List.of());
        problems.record(withTrace);
        problems.record(withoutMessage);
        problems.record(bare);

        stored = problems.find(created.id()).orElseThrow();
        assertEquals("trace two", stored.stacktrace(), "an occurrence without a trace keeps the last one");
        assertNull(stored.exceptionMessage(), "the message belongs to the same occurrence as the trace");
    }

    @Test
    void aLaterRecordWithAnEarlierOccurrenceMovesTheFirstSightingBack() {
        String fp = fingerprint();
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        var created = problems.record(record(fp, ProblemLevel.ERROR, List.of("x"), now));

        problems.record(record(fp, ProblemLevel.ERROR, List.of("x"), now.minusSeconds(30)));

        var stored = problems.find(created.id()).orElseThrow();
        assertEquals(now.minusSeconds(30), stored.firstSeen());
        assertEquals(now, stored.lastSeen());
    }

    @Test
    void oneRecordWithManyOccurrencesKeepsTheNewest() {
        Instant start = Instant.now().minus(1, ChronoUnit.HOURS).truncatedTo(ChronoUnit.MILLIS);
        List<ProblemOccurrence> newestFirst = IntStream.range(0, 8)
                .mapToObj(i -> new ProblemOccurrence(start.plusSeconds(7 - i), "worker-" + i))
                .toList();
        var record = new ProblemRecord(
                fingerprint(),
                ProblemLevel.ERROR,
                "dev.chojo.ember.Test",
                "x",
                null,
                null,
                null,
                List.of("x"),
                8,
                start,
                start.plusSeconds(7),
                newestFirst);

        var created = problems.record(record);

        assertEquals(newestFirst.subList(0, ProblemLogRepository.OCCURRENCE_LIMIT), problems.occurrences(created.id()));
    }

    @Test
    void aResolvedProblemThatComesBackIsOpenAgain() {
        String fp = fingerprint();
        var created = problems.record(record(fp, ProblemLevel.ERROR, List.of("x"), Instant.now()));
        int admin = accountRepo.create(fp + "@problems.test", "Ada", "Admin").id();
        assertTrue(problems.setState(created.id(), Set.of(ProblemState.OPEN), ProblemState.RESOLVED, admin));

        var again = problems.record(record(fp, ProblemLevel.ERROR, List.of("x"), Instant.now()));

        assertEquals(ProblemState.RESOLVED, again.previous());
        assertEquals(ProblemState.OPEN, again.state());
        assertTrue(again.newOrReopened());
        var stored = problems.find(created.id()).orElseThrow();
        assertEquals(1, stored.reopenedCount());
        assertTrue(stored.stateChangedAt() != null, "coming back is a change of state");
        assertFalse(
                problems.setState(Long.MAX_VALUE, Set.of(ProblemState.OPEN), ProblemState.RESOLVED, admin),
                "nothing to change");
    }

    @Test
    void aMutedProblemOnlyCounts() {
        String fp = fingerprint();
        var created = problems.record(record(fp, ProblemLevel.WARN, List.of("noise"), Instant.now()));
        int admin = accountRepo.create(fp + "@problems.test", "Ada", "Admin").id();
        problems.setState(created.id(), Set.of(ProblemState.OPEN), ProblemState.MUTED, admin);

        var again = problems.record(record(fp, ProblemLevel.WARN, List.of("noise"), Instant.now()));

        assertEquals(ProblemState.MUTED, again.state());
        assertFalse(again.newOrReopened());
        assertTrue(problems.mutedFingerprints().contains(fp));
        assertEquals(2, problems.find(created.id()).orElseThrow().count());
    }

    @Test
    void onlyTheNewestOccurrencesAreKept() {
        String fp = fingerprint();
        Instant start = Instant.now().minus(1, ChronoUnit.HOURS).truncatedTo(ChronoUnit.MILLIS);
        long id = 0;
        for (int i = 0; i < 7; i++) {
            id = problems.record(record(fp, ProblemLevel.ERROR, List.of("x"), start.plusSeconds(i)))
                    .id();
        }

        var occurrences = problems.occurrences(id);

        assertEquals(ProblemLogRepository.OCCURRENCE_LIMIT, occurrences.size());
        assertEquals(start.plusSeconds(6), occurrences.getFirst().occurredAt(), "the newest first");
        assertEquals(start.plusSeconds(2), occurrences.getLast().occurredAt());
    }

    @Test
    void refusalsAreNeverWaitingAndResolvingAllLeavesThemAlone() {
        int before = problems.countOpen();
        var fault = problems.record(record(fingerprint(), ProblemLevel.ERROR, List.of("fault"), Instant.now()));
        var refusal = problems.record(record(fingerprint(), ProblemLevel.REFUSAL, List.of("409"), Instant.now()));

        assertEquals(before + 1, problems.countOpen());

        int admin = accountRepo
                .create(fingerprint() + "@problems.test", "Ada", "Admin")
                .id();
        assertTrue(problems.resolveAllOpen(Set.of(ProblemLevel.WARN, ProblemLevel.ERROR), admin) >= 1);
        assertEquals(
                ProblemState.RESOLVED, problems.find(fault.id()).orElseThrow().state());
        assertEquals(
                ProblemState.OPEN, problems.find(refusal.id()).orElseThrow().state());
    }

    @Test
    void theListFiltersByStateTheNewestFirst() {
        var older = problems.record(record(
                fingerprint(), ProblemLevel.ERROR, List.of("a"), Instant.now().minusSeconds(60)));
        var newer = problems.record(record(fingerprint(), ProblemLevel.ERROR, List.of("b"), Instant.now()));
        int admin = accountRepo
                .create(fingerprint() + "@problems.test", "Ada", "Admin")
                .id();
        problems.setState(older.id(), Set.of(ProblemState.OPEN), ProblemState.MUTED, admin);

        var muted = problems.list(Set.of(ProblemState.MUTED), false);
        var all = problems.list(Set.of(), false);
        var openOrMuted = problems.list(Set.of(ProblemState.OPEN, ProblemState.MUTED), false);

        assertTrue(openOrMuted.stream().anyMatch(p -> p.id() == older.id()));
        assertTrue(openOrMuted.stream().anyMatch(p -> p.id() == newer.id()));

        assertTrue(muted.stream().anyMatch(p -> p.id() == older.id()));
        assertTrue(muted.stream().noneMatch(p -> p.id() == newer.id()));
        assertTrue(all.indexOf(all.stream()
                        .filter(p -> p.id() == newer.id())
                        .findFirst()
                        .orElseThrow())
                < all.indexOf(all.stream()
                        .filter(p -> p.id() == older.id())
                        .findFirst()
                        .orElseThrow()));
    }

    @Test
    void aProblemGoesOnceItsRetentionPassedAndTakesItsOccurrencesAlong() {
        var old = problems.record(record(fingerprint(), ProblemLevel.ERROR, List.of("old"), Instant.now()));
        var fresh = problems.record(record(fingerprint(), ProblemLevel.ERROR, List.of("fresh"), Instant.now()));
        query("UPDATE problem_log SET last_seen = now() - INTERVAL '40 days' WHERE id = :id;")
                .single(call().bind("id", old.id()))
                .update();

        assertTrue(problems.prune(30) >= 1);

        assertTrue(problems.find(old.id()).isEmpty());
        assertTrue(problems.occurrences(old.id()).isEmpty());
        assertTrue(problems.find(fresh.id()).isPresent());
        assertTrue(problems.delete(fresh.id()));
        assertFalse(problems.delete(fresh.id()));
    }

    @Test
    void anAdministratorWhoLeavesLeavesTheStateBehind() {
        var created = problems.record(record(fingerprint(), ProblemLevel.ERROR, List.of("x"), Instant.now()));
        var admin = accountRepo.create(fingerprint() + "@problems.test", "Ada", "Admin");
        problems.setState(created.id(), Set.of(ProblemState.OPEN), ProblemState.RESOLVED, admin.id());

        accountRepo.delete(admin.id());

        assertEquals(
                ProblemState.RESOLVED, problems.find(created.id()).orElseThrow().state());
    }

    @Test
    void refusalsAreListedApartTheMostFrequentFirst() {
        var fault = problems.record(record(fingerprint(), ProblemLevel.WARN, List.of("f"), Instant.now()));
        var rare = problems.record(record(fingerprint(), ProblemLevel.REFUSAL, List.of("r"), Instant.now()));
        var often = problems.record(record(
                fingerprint(),
                ProblemLevel.REFUSAL,
                List.of("o1", "o2", "o3"),
                Instant.now().minusSeconds(60)));

        var refusals = problems.list(Set.of(), true);
        var faults = problems.list(Set.of(), false);

        assertTrue(refusals.stream().noneMatch(p -> p.id() == fault.id()));
        assertTrue(faults.stream().noneMatch(p -> p.id() == rare.id() || p.id() == often.id()));
        var ids = refusals.stream().map(StoredProblem::id).toList();
        assertTrue(ids.indexOf(often.id()) < ids.indexOf(rare.id()), "the more frequent refusal comes first");
    }

    @Test
    void aChangeFromTheWrongStateLeavesTheProblemAlone() {
        var created = problems.record(record(fingerprint(), ProblemLevel.ERROR, List.of("x"), Instant.now()));
        int first = accountRepo
                .create(fingerprint() + "@problems.test", "Ada", "Admin")
                .id();
        int second = accountRepo
                .create(fingerprint() + "@problems.test", "Bo", "Admin")
                .id();
        problems.setState(created.id(), Set.of(ProblemState.OPEN), ProblemState.RESOLVED, first);
        var resolved = problems.find(created.id()).orElseThrow();

        assertTrue(
                problems.setState(created.id(), Set.of(ProblemState.MUTED), ProblemState.OPEN, second),
                "the problem is there");
        assertTrue(problems.setState(created.id(), Set.of(ProblemState.OPEN), ProblemState.RESOLVED, second));

        var after = problems.find(created.id()).orElseThrow();
        assertEquals(ProblemState.RESOLVED, after.state(), "unmuting a resolved problem does not reopen it");
        assertEquals(resolved.stateChangedAt(), after.stateChangedAt(), "resolving again keeps the first resolve");
    }
}
