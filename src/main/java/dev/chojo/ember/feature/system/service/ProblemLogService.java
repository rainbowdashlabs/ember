/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.system.service;

import dev.chojo.ember.api.refusal.SystemRefusal;
import dev.chojo.ember.conf.file.elements.Metrics;
import dev.chojo.ember.feature.system.entity.ProblemLevel;
import dev.chojo.ember.feature.system.entity.ProblemState;
import dev.chojo.ember.feature.system.entity.StoredProblem;
import dev.chojo.ember.feature.system.repository.ProblemLogRepository;
import dev.chojo.ember.lifecycle.Schedule;
import dev.chojo.ember.lifecycle.ScheduledTask;
import dev.chojo.ember.lifecycle.TaskSource;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
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

    private final ProblemLogRepository repository;
    private final Metrics metrics;

    @Inject
    public ProblemLogService(ProblemLogRepository repository, Metrics metrics) {
        this.repository = repository;
        this.metrics = metrics;
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
