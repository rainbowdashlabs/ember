/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.system.repository;

import de.chojo.sadu.postgresql.types.PostgreSqlTypes;
import dev.chojo.ember.feature.system.entity.ProblemLevel;
import dev.chojo.ember.feature.system.entity.ProblemOccurrence;
import dev.chojo.ember.feature.system.entity.ProblemRecord;
import dev.chojo.ember.feature.system.entity.ProblemState;
import dev.chojo.ember.feature.system.entity.StoredProblem;
import dev.chojo.ember.util.sql.Transactions;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

/**
 * The problem log as rows: added to by the recorder, read and changed by the administration, pruned
 * by age.
 */
@Singleton
public class ProblemLogRepository {

    /** How many different messages a problem keeps. */
    public static final int MESSAGE_LIMIT = 20;

    /** How many problems a list holds at most. */
    public static final int LIST_LIMIT = 500;

    /** How many occurrences a problem keeps beside it. */
    public static final int OCCURRENCE_LIMIT = 5;

    /**
     * What writing a record did to the stored problem.
     *
     * @param id       the stored problem
     * @param state    its state after the write
     * @param previous its state before, or null where the write made it
     */
    public record Recorded(
            long id, ProblemState state, @Nullable ProblemState previous) {

        /** Whether the problem is new or came back after it was resolved, which is when it is worth telling. */
        public boolean newOrReopened() {
            return previous == null || (previous == ProblemState.RESOLVED && state == ProblemState.OPEN);
        }
    }

    /**
     * Adds what was gathered about one problem to its row, making the row where there is none.
     *
     * <p>The count adds up, the last time moves on, the latest stacktrace replaces the stored one and new
     * messages join the list up to its limit. A resolved problem that happens again is open again and
     * counts as reopened; an open or muted one keeps its state. The occurrences are written beside it
     * and only the newest are kept. All of it is one transaction, so a problem never stands without its
     * occurrences.
     *
     * @param record what was gathered
     * @return what the write did
     */
    public Recorded record(ProblemRecord record) {
        return Transactions.call(() -> {
            ProblemState previous = lockedState(record.fingerprint());
            var recorded = upsert(record, previous);
            writeOccurrences(recorded.id(), record.occurrences());
            return recorded;
        });
    }

    /**
     * The state of the stored problem, holding its row until the write is done, so an administrator
     * resolving it in between cannot make the write report a state the row no longer had.
     */
    private @Nullable ProblemState lockedState(String fingerprint) {
        return query("SELECT state FROM problem_log WHERE fingerprint = :fingerprint FOR UPDATE;")
                .single(call().bind("fingerprint", fingerprint))
                .map(row -> row.getEnum("state", ProblemState.class))
                .first()
                .orElse(null);
    }

    private Recorded upsert(ProblemRecord record, @Nullable ProblemState previous) {
        return query("""
                        WITH incoming AS (
                            SELECT ARRAY(SELECT m FROM unnest(:messages::TEXT[]) WITH ORDINALITY AS u(m, n)
                                         GROUP BY m ORDER BY min(n)) AS messages
                        )
                        INSERT INTO problem_log(fingerprint, level, logger, message, exception_class,
                                                exception_message, stacktrace, distinct_messages, count,
                                                first_seen, last_seen)
                        SELECT :fingerprint, :level, :logger, :message, :exception_class, :exception_message,
                               :stacktrace, (incoming.messages)[1:%d], :count, :first_seen, :last_seen
                        FROM incoming
                        ON CONFLICT (fingerprint) DO UPDATE
                            SET count             = problem_log.count + excluded.count,
                                last_seen         = greatest(problem_log.last_seen, excluded.last_seen),
                                first_seen        = least(problem_log.first_seen, excluded.first_seen),
                                exception_message = CASE WHEN excluded.stacktrace IS NOT NULL
                                                         THEN excluded.exception_message
                                                         ELSE problem_log.exception_message END,
                                stacktrace        = coalesce(excluded.stacktrace, problem_log.stacktrace),
                                distinct_messages = (problem_log.distinct_messages || ARRAY(
                                    SELECT m FROM unnest(excluded.distinct_messages) WITH ORDINALITY AS u(m, n)
                                    WHERE m <> ALL (problem_log.distinct_messages)
                                    ORDER BY n))[1:%d],
                                state             = CASE WHEN problem_log.state = 'RESOLVED' THEN 'OPEN'
                                                         ELSE problem_log.state END,
                                state_changed_at  = CASE WHEN problem_log.state = 'RESOLVED' THEN now()
                                                         ELSE problem_log.state_changed_at END,
                                state_changed_by  = CASE WHEN problem_log.state = 'RESOLVED' THEN NULL
                                                         ELSE problem_log.state_changed_by END,
                                reopened_count    = problem_log.reopened_count
                                    + CASE WHEN problem_log.state = 'RESOLVED' THEN 1 ELSE 0 END
                        RETURNING id, state;""".formatted(MESSAGE_LIMIT, MESSAGE_LIMIT))
                .single(call().bind("fingerprint", record.fingerprint())
                        .bind("level", record.level())
                        .bind("logger", record.logger())
                        .bind("message", record.message())
                        .bind("exception_class", record.exceptionClass())
                        .bind("exception_message", record.exceptionMessage())
                        .bind("stacktrace", record.stacktrace())
                        .bind("messages", List.copyOf(record.messages()), PostgreSqlTypes.TEXT)
                        .bind("count", record.count())
                        .bind("first_seen", record.firstSeen(), INSTANT_TIMESTAMP)
                        .bind("last_seen", record.lastSeen(), INSTANT_TIMESTAMP))
                .map(row -> new Recorded(row.getLong("id"), row.getEnum("state", ProblemState.class), previous))
                .first()
                .orElseThrow();
    }

    private void writeOccurrences(long problemId, List<ProblemOccurrence> occurrences) {
        if (occurrences.isEmpty()) return;
        var calls = occurrences.stream()
                .limit(OCCURRENCE_LIMIT)
                .map(occurrence -> call().bind("problem", problemId)
                        .bind("at", occurrence.occurredAt(), INSTANT_TIMESTAMP)
                        .bind("thread", occurrence.thread()))
                .toList();
        query("INSERT INTO problem_occurrence(problem_id, occurred_at, thread) VALUES (:problem, :at, :thread);")
                .batch(calls)
                .insert();
        query("""
                        DELETE FROM problem_occurrence
                        WHERE problem_id = :problem
                          AND id NOT IN (SELECT id FROM problem_occurrence WHERE problem_id = :problem
                                         ORDER BY occurred_at DESC, id DESC LIMIT %d);""".formatted(OCCURRENCE_LIMIT))
                .single(call().bind("problem", problemId))
                .delete();
    }

    /**
     * The problems in the given states, either the faults or the refusals.
     *
     * <p>At most {@link #LIST_LIMIT} are listed. Faults come the most recent first, because what
     * happened last is what to look at; refusals the most frequent first, because the refusal readers
     * hit most is the one worth looking at.
     *
     * @param states    the states to list, every state when empty
     * @param refusals  whether to list the refusals instead of the faults
     * @return the problems
     */
    public List<StoredProblem> list(Collection<ProblemState> states, boolean refusals) {
        List<String> names = states.stream().map(Enum::name).toList();
        String order = refusals ? "count DESC, last_seen DESC, id DESC" : "last_seen DESC, id DESC";
        return query("SELECT " + StoredProblem.COLUMNS + """
                         FROM problem_log
                        WHERE (cardinality(:states::TEXT[]) = 0 OR state = ANY(:states::TEXT[]))
                          AND (level = 'REFUSAL') = :refusals
                        ORDER BY %s
                        LIMIT %d;""".formatted(order, LIST_LIMIT))
                .single(call().bind("states", names, PostgreSqlTypes.TEXT).bind("refusals", refusals))
                .map(StoredProblem.map())
                .all();
    }

    /**
     * One problem.
     *
     * @param id the problem
     * @return the problem, or empty where there is none
     */
    public Optional<StoredProblem> find(long id) {
        return query("SELECT " + StoredProblem.COLUMNS + " FROM problem_log WHERE id = :id;")
                .single(call().bind("id", id))
                .map(StoredProblem.map())
                .first();
    }

    /**
     * Sets the state of a problem by hand, where it stands in one of the states the change starts from.
     *
     * <p>A problem in any other state is left as it is, so a page that is out of date cannot reopen a
     * resolved problem by unmuting it, and resolving a resolved problem keeps who resolved it first.
     *
     * @param id      the problem
     * @param from    the states the change may start from
     * @param to      its new state
     * @param account the administrator setting it
     * @return whether the problem is there, changed or not
     */
    public boolean setState(long id, Set<ProblemState> from, ProblemState to, int account) {
        List<String> names = from.stream().map(Enum::name).toList();
        boolean changed = query("""
                        UPDATE problem_log
                        SET state = :state, state_changed_at = now(), state_changed_by = :account
                        WHERE id = :id AND state = ANY(:from::TEXT[]);""")
                .single(call().bind("id", id)
                        .bind("state", to)
                        .bind("account", account)
                        .bind("from", names, PostgreSqlTypes.TEXT))
                .update()
                .changed();
        return changed || exists(id);
    }

    private boolean exists(long id) {
        return query("SELECT 1 AS one FROM problem_log WHERE id = :id;")
                .single(call().bind("id", id))
                .map(row -> row.getInt("one"))
                .first()
                .isPresent();
    }

    /**
     * Resolves every open problem of the given levels.
     *
     * @param levels  the levels to resolve
     * @param account the administrator resolving them
     * @return how many were resolved
     */
    public int resolveAllOpen(Collection<ProblemLevel> levels, int account) {
        List<String> names = levels.stream().map(Enum::name).toList();
        return query("""
                        UPDATE problem_log
                        SET state = 'RESOLVED', state_changed_at = now(), state_changed_by = :account
                        WHERE state = 'OPEN' AND level = ANY(:levels::TEXT[]);""")
                .single(call().bind("levels", names, PostgreSqlTypes.TEXT).bind("account", account))
                .update()
                .rows();
    }

    /**
     * Deletes a problem and its occurrences.
     *
     * @param id the problem
     * @return whether it was there
     */
    public boolean delete(long id) {
        return query("DELETE FROM problem_log WHERE id = :id;")
                .single(call().bind("id", id))
                .delete()
                .changed();
    }

    /**
     * Deletes the problems that last happened longer ago than the given number of days. Fewer than one
     * day is read as one, so a setting of zero never empties the log at once.
     *
     * @param keepDays how many days a problem is kept after it last happened
     * @return how many were deleted
     */
    public int prune(int keepDays) {
        return query("DELETE FROM problem_log WHERE last_seen < now() - make_interval(days => :days);")
                .single(call().bind("days", Math.max(1, keepDays)))
                .delete()
                .rows();
    }

    /**
     * How many problems wait for an administrator: open warnings and errors. A refusal is never waiting.
     *
     * @return the number of open problems
     */
    public int countOpen() {
        return query("SELECT count(*) AS open FROM problem_log WHERE state = 'OPEN' AND level <> 'REFUSAL';")
                .single(call())
                .map(row -> row.getInt("open"))
                .first()
                .orElse(0);
    }

    /**
     * Whether the database answers at all, which tells a refused problem from an outage.
     *
     * @return whether a trivial query went through
     */
    public boolean reachable() {
        try {
            return query("SELECT 1 AS one;")
                    .single(call())
                    .map(row -> row.getInt("one"))
                    .first()
                    .isPresent();
        } catch (RuntimeException e) {
            return false;
        }
    }

    /**
     * The occurrences kept beside a problem, the newest first.
     *
     * @param problemId the problem
     * @return its occurrences
     */
    public List<ProblemOccurrence> occurrences(long problemId) {
        return query("""
                        SELECT occurred_at, thread FROM problem_occurrence
                        WHERE problem_id = :problem
                        ORDER BY occurred_at DESC, id DESC;""")
                .single(call().bind("problem", problemId))
                .map(row -> new ProblemOccurrence(row.get("occurred_at", INSTANT_TIMESTAMP), row.getString("thread")))
                .all();
    }
}
