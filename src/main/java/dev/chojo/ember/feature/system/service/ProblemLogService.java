/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.system.service;

import dev.chojo.ember.api.refusal.SystemRefusal;
import dev.chojo.ember.conf.Conf;
import dev.chojo.ember.conf.file.elements.Metrics;
import dev.chojo.ember.feature.system.entity.ProblemContext;
import dev.chojo.ember.feature.system.entity.ProblemContextLine;
import dev.chojo.ember.feature.system.entity.ProblemContextState;
import dev.chojo.ember.feature.system.entity.ProblemLevel;
import dev.chojo.ember.feature.system.entity.ProblemOccurrence;
import dev.chojo.ember.feature.system.entity.ProblemState;
import dev.chojo.ember.feature.system.entity.StoredProblem;
import dev.chojo.ember.feature.system.repository.ApplicationLogRepository;
import dev.chojo.ember.feature.system.repository.ProblemLogRepository;
import dev.chojo.ember.lifecycle.Schedule;
import dev.chojo.ember.lifecycle.ScheduledTask;
import dev.chojo.ember.lifecycle.TaskSource;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * The problem log as the administration works with it: listing, resolving, muting and deleting what
 * was recorded, and letting go of what has not happened for longer than the configured retention.
 *
 * <p>Faults and refusals are worked through apart. A resolve-all on one never touches the other, so
 * clearing the refusals readers keep hitting does not also clear a fault nobody has looked at yet.
 */
@Singleton
public class ProblemLogService implements TaskSource {
    private static final Logger log = LoggerFactory.getLogger(ProblemLogService.class);
    private static final Duration SWEEP_INTERVAL = Duration.ofDays(1);

    /** How far before an occurrence its surrounding lines reach, and how many of them at most. */
    static final Duration CONTEXT_BEFORE = Duration.ofSeconds(30);

    static final int CONTEXT_BEFORE_LINES = 50;

    /** How far after an occurrence its surrounding lines reach, and how many of them at most. */
    public static final Duration CONTEXT_AFTER = Duration.ofSeconds(5);

    static final int CONTEXT_AFTER_LINES = 10;

    private final ProblemLogRepository repository;
    private final ApplicationLogRepository logs;
    private final Metrics metrics;
    private final Conf conf;

    @Inject
    public ProblemLogService(
            ProblemLogRepository repository, ApplicationLogRepository logs, Metrics metrics, Conf conf) {
        this.repository = repository;
        this.logs = logs;
        this.metrics = metrics;
        this.conf = conf;
    }

    /**
     * The problems in the given states.
     *
     * @param states   the states to list, every state when empty
     * @param refusals whether to list the refusals instead of the faults
     * @return the problems, faults the most recent first and refusals the most frequent first
     */
    public List<StoredProblem> list(Collection<ProblemState> states, boolean refusals) {
        return repository.list(states, refusals);
    }

    /**
     * Marks a problem as dealt with. It opens again when it happens again.
     *
     * @param id      the problem
     * @param account the administrator
     */
    public void resolve(long id, int account) {
        setState(id, Set.of(ProblemState.OPEN), ProblemState.RESOLVED, account);
    }

    /**
     * Keeps a problem out of the waiting count and the beacon for good, however often it happens.
     *
     * @param id      the problem
     * @param account the administrator
     */
    public void mute(long id, int account) {
        setState(id, Set.of(ProblemState.OPEN, ProblemState.RESOLVED), ProblemState.MUTED, account);
    }

    /**
     * Takes a problem back from muted to open.
     *
     * @param id      the problem
     * @param account the administrator
     */
    public void unmute(long id, int account) {
        setState(id, Set.of(ProblemState.MUTED), ProblemState.OPEN, account);
    }

    /**
     * Moves a problem to a state where it stands in one the move starts from. A problem in another
     * state is left alone without complaint, because the list the administrator acted on was older
     * than the problem's state; the next refresh shows where it stands.
     */
    private void setState(long id, Set<ProblemState> from, ProblemState to, int account) {
        if (!repository.setState(id, from, to, account)) throw SystemRefusal.PROBLEM_NOT_HERE.raise();
    }

    /**
     * Resolves every open fault, or every open refusal.
     *
     * @param refusals whether to resolve the refusals instead of the faults
     * @param account  the administrator
     * @return how many were resolved
     */
    public int resolveAll(boolean refusals, int account) {
        Set<ProblemLevel> levels =
                refusals ? Set.of(ProblemLevel.REFUSAL) : Set.of(ProblemLevel.WARN, ProblemLevel.ERROR);
        return repository.resolveAllOpen(levels, account);
    }

    /**
     * Deletes a problem now. Should it happen again, it is recorded as a new one.
     *
     * @param id the problem
     */
    public void delete(long id) {
        if (!repository.delete(id)) throw SystemRefusal.PROBLEM_NOT_HERE.raise();
    }

    /**
     * The log around one occurrence of a problem: the lines of its thread from {@link #CONTEXT_BEFORE}
     * before to {@link #CONTEXT_AFTER} after it, at most {@link #CONTEXT_BEFORE_LINES} and
     * {@link #CONTEXT_AFTER_LINES} of them, or the lines of every thread in that window.
     *
     * <p>The occurrence is named by its time, not by its place in the list, because the list moves on
     * while the problem keeps happening. One that is no longer kept falls back to the newest, and the
     * answer says which it is around.
     *
     * <p>Only what the stored log kept can be shown, so its own levels and retention decide. Where it
     * kept nothing, the answer says whether that is because it is not written at all. The line that is
     * the occurrence itself is marked: the first one at the same moment, on the same thread, from the
     * same logger and at the problem's level.
     *
     * @param id         the problem
     * @param at         when the occurrence happened, or null for the newest
     * @param allThreads whether to list every thread, not only the occurrence's own
     * @return the lines and what they are around
     */
    public ProblemContext context(long id, @Nullable Instant at, boolean allThreads) {
        var problem = repository.find(id).orElseThrow(SystemRefusal.PROBLEM_NOT_HERE::raise);
        var occurrences = repository.occurrences(id);
        boolean databaseLogging = conf.main().logging().databaseEnabled();
        if (occurrences.isEmpty()) {
            return new ProblemContext(
                    ProblemContextState.NO_OCCURRENCE, databaseLogging, occurrences, 0, allThreads, List.of());
        }
        int index = at == null ? 0 : Math.max(0, indexOf(occurrences, at));
        var chosen = occurrences.get(index);
        var lines = marked(
                logs.around(
                        chosen.occurredAt(),
                        allThreads ? null : chosen.thread(),
                        chosen.occurredAt().minus(CONTEXT_BEFORE),
                        chosen.occurredAt().plus(CONTEXT_AFTER),
                        CONTEXT_BEFORE_LINES,
                        CONTEXT_AFTER_LINES),
                chosen,
                problem);
        ProblemContextState state = !lines.isEmpty()
                ? ProblemContextState.LINES
                : databaseLogging ? ProblemContextState.NOTHING_KEPT : ProblemContextState.LOGGING_OFF;
        return new ProblemContext(state, databaseLogging, occurrences, index, allThreads, lines);
    }

    private static int indexOf(List<ProblemOccurrence> occurrences, Instant at) {
        for (int i = 0; i < occurrences.size(); i++) {
            if (occurrences.get(i).occurredAt().equals(at)) return i;
        }
        return -1;
    }

    private static List<ProblemContextLine> marked(
            List<ApplicationLogRepository.LogEntry> lines, ProblemOccurrence occurrence, StoredProblem problem) {
        boolean found = false;
        List<ProblemContextLine> result = new ArrayList<>(lines.size());
        for (var line : lines) {
            boolean isIt = !found && isTheOccurrence(line, occurrence, problem);
            found |= isIt;
            result.add(new ProblemContextLine(
                    line.loggedAt(), line.level(), line.logger(), line.thread(), line.message(), isIt));
        }
        return result;
    }

    private static boolean isTheOccurrence(
            ApplicationLogRepository.LogEntry line, ProblemOccurrence occurrence, StoredProblem problem) {
        String level = problem.level() == ProblemLevel.ERROR ? "ERROR" : "WARN";
        return line.loggedAt().equals(occurrence.occurredAt())
                && line.thread().equals(occurrence.thread())
                && line.logger().equals(problem.logger())
                && line.level().equals(level);
    }

    /** Deletes the problems that last happened longer ago than the retention. Reachable by tests. */
    void sweep() {
        try {
            int deleted = repository.prune(metrics.problemRetentionDays());
            if (deleted > 0)
                log.info("Swept {} problems not seen for {} days", deleted, metrics.problemRetentionDays());
        } catch (RuntimeException e) {
            log.warn("Sweeping the problem log failed", e);
        }
    }

    @Override
    public List<ScheduledTask> scheduledTasks() {
        return List.of(new ScheduledTask(
                "problem-log-sweep", Schedule.fixedDelay(Duration.ofMinutes(10), SWEEP_INTERVAL), this::sweep));
    }
}
