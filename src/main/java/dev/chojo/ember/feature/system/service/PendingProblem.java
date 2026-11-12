/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.system.service;

import dev.chojo.ember.feature.system.entity.ProblemLevel;
import dev.chojo.ember.feature.system.entity.ProblemOccurrence;
import dev.chojo.ember.feature.system.entity.ProblemRecord;
import dev.chojo.ember.feature.system.repository.ProblemLogRepository;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

/**
 * What the problem log gathered about one problem since it last wrote to the database.
 *
 * <p>Runs on every thread that logs a warning, so it only counts and keeps what the stored row needs: the
 * count, the first and last time, the latest stacktrace and exception message, the new messages up to the
 * row's limit and the newest occurrences. Everything else is the database's business. A stacktrace is
 * only rendered for an occurrence that becomes the latest, and every text is cut to a length the row can
 * hold, so a flood of one error costs little and a buffer of them stays small. Characters PostgreSQL
 * refuses in text are taken out.
 */
final class PendingProblem {
    /** The longest stacktrace kept, in characters. */
    static final int STACKTRACE_LIMIT = 64 * 1024;

    /** The longest message kept, in characters. */
    static final int MESSAGE_LIMIT = 4 * 1024;

    private final String fingerprint;
    private final ProblemLevel level;
    private final String logger;
    private String firstMessage;
    private final @Nullable String exceptionClass;
    private final Set<String> messages = new LinkedHashSet<>();
    private final Deque<ProblemOccurrence> occurrences = new ArrayDeque<>();
    private @Nullable String exceptionMessage;
    private @Nullable String stacktrace;
    private long count;
    private Instant firstSeen;
    private Instant lastSeen;
    private int failedWrites;

    PendingProblem(
            String fingerprint,
            ProblemLevel level,
            String logger,
            String message,
            @Nullable String exceptionClass,
            Instant at) {
        this.fingerprint = fingerprint;
        this.level = level;
        this.logger = logger;
        this.firstMessage = clean(message, MESSAGE_LIMIT);
        this.exceptionClass = exceptionClass;
        this.firstSeen = at;
        this.lastSeen = at;
    }

    /**
     * Counts one more occurrence.
     *
     * @param message          what it was logged with
     * @param exceptionMessage the exception's message, or null
     * @param stacktrace       renders the stacktrace, or gives null without an exception; asked only when
     *                         this occurrence becomes the latest
     * @param at               when it happened
     * @param thread           the thread that logged it
     */
    synchronized void add(
            String message,
            @Nullable String exceptionMessage,
            Supplier<@Nullable String> stacktrace,
            Instant at,
            String thread) {
        count++;
        if (at.isBefore(firstSeen)) firstSeen = at;
        if (!at.isBefore(lastSeen) || this.stacktrace == null) {
            if (!at.isBefore(lastSeen)) lastSeen = at;
            String rendered = stacktrace.get();
            if (rendered != null) {
                this.stacktrace = clean(rendered, STACKTRACE_LIMIT);
                this.exceptionMessage = clean(exceptionMessage, MESSAGE_LIMIT);
            }
        }
        if (messages.size() < ProblemLogRepository.MESSAGE_LIMIT) messages.add(clean(message, MESSAGE_LIMIT));
        occurrences.addFirst(new ProblemOccurrence(at, thread));
        while (occurrences.size() > ProblemLogRepository.OCCURRENCE_LIMIT) occurrences.removeLast();
    }

    /**
     * Takes in what another gathering of the same problem holds, which is how a write that failed is put
     * back without losing what was gathered meanwhile.
     *
     * @param other the same problem, gathered earlier
     */
    synchronized void absorb(PendingProblem other) {
        var record = other.record();
        count += record.count();
        failedWrites = Math.max(failedWrites, other.failedWrites());
        if (record.firstSeen().isBefore(firstSeen)) {
            firstSeen = record.firstSeen();
            firstMessage = record.message();
        }
        if (record.lastSeen().isAfter(lastSeen)) {
            lastSeen = record.lastSeen();
            if (record.stacktrace() != null) {
                stacktrace = record.stacktrace();
                exceptionMessage = record.exceptionMessage();
            }
        } else if (stacktrace == null) {
            stacktrace = record.stacktrace();
            exceptionMessage = record.exceptionMessage();
        }
        var merged = new LinkedHashSet<>(record.messages());
        merged.addAll(messages);
        messages.clear();
        merged.stream().limit(ProblemLogRepository.MESSAGE_LIMIT).forEach(messages::add);
        var all = new ArrayList<>(occurrences);
        all.addAll(record.occurrences());
        all.sort((a, b) -> b.occurredAt().compareTo(a.occurredAt()));
        occurrences.clear();
        all.stream().limit(ProblemLogRepository.OCCURRENCE_LIMIT).forEach(occurrences::addLast);
    }

    /**
     * Counts another problem on this one without taking its details, which is how a problem put back
     * finds room on the overflow entry: its count and its times, and one line naming it.
     *
     * @param other the problem to count in
     */
    synchronized void countIn(ProblemRecord other) {
        count += other.count();
        if (other.firstSeen().isBefore(firstSeen)) firstSeen = other.firstSeen();
        if (other.lastSeen().isAfter(lastSeen)) lastSeen = other.lastSeen();
        if (messages.size() < ProblemLogRepository.MESSAGE_LIMIT) {
            messages.add(clean(other.logger() + ": " + other.message(), MESSAGE_LIMIT));
        }
    }

    String fingerprint() {
        return fingerprint;
    }

    /** How often writing this problem failed in a row, which tells a refused record from an outage. */
    synchronized int failedWrites() {
        return failedWrites;
    }

    /** Notes one more failed write. */
    synchronized void failedOnce() {
        failedWrites++;
    }

    private static @Nullable String clean(@Nullable String text, int limit) {
        if (text == null) return null;
        String without = text.indexOf('\u0000') < 0 ? text : text.replace("\u0000", "");
        return without.length() > limit ? without.substring(0, limit) : without;
    }

    /**
     * What is gathered, as the repository takes it.
     *
     * @return the record
     */
    synchronized ProblemRecord record() {
        return new ProblemRecord(
                fingerprint,
                level,
                logger,
                firstMessage,
                exceptionClass,
                exceptionMessage,
                stacktrace,
                List.copyOf(messages),
                count,
                firstSeen,
                lastSeen,
                List.copyOf(occurrences));
    }
}
