/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.procedure.service;

import dev.chojo.ember.feature.procedure.entity.ProcedureItem;
import dev.chojo.ember.feature.procedure.entity.ProcedureStepKind;
import dev.chojo.ember.feature.procedure.repository.ProcedureRepository;
import dev.chojo.ember.lifecycle.Schedule;
import dev.chojo.ember.lifecycle.ScheduledTask;
import dev.chojo.ember.lifecycle.TaskSource;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Reminds of the signatures a procedure's document steps still wait for once it falls due within
 * {@link #AHEAD}, on top of the weekly reminders every request gets.
 *
 * <p>Each procedure is reminded of once for its due date, for whatever is open at that moment: the
 * reminder is recorded before it is sent and only for the due date it was read with, so a run that overlaps
 * another never reminds twice, and a changed due date makes it due again. A request asked for less than
 * {@link #SETTLED} ago is left alone, as its own mail is still fresh; a step unlocking later gets its
 * request mail and no due reminder. A procedure about nobody, resolved, or already past its due date is
 * left alone. The sweep looks in every hour.
 */
@Singleton
public class ProcedureDueReminders implements TaskSource {
    private static final Logger log = LoggerFactory.getLogger(ProcedureDueReminders.class);
    private static final Duration START_DELAY = Duration.ofMinutes(25);
    private static final Duration SWEEP_INTERVAL = Duration.ofHours(1);

    /** How long before the due date the reminder goes out. */
    static final Duration AHEAD = Duration.ofDays(2);

    /** How long a request has to stand before it is reminded of, so it is not followed by a reminder at once. */
    static final Duration SETTLED = Duration.ofDays(1);

    /** How many procedures one run reminds of at most. */
    static final int MAX_PER_RUN = 100;

    private final ProcedureRepository repository;
    private final DocumentStepSigning signing;
    private final Clock clock;

    @Inject
    public ProcedureDueReminders(ProcedureRepository repository, DocumentStepSigning signing) {
        this(repository, signing, Clock.systemUTC());
    }

    ProcedureDueReminders(ProcedureRepository repository, DocumentStepSigning signing, Clock clock) {
        this.repository = repository;
        this.signing = signing;
        this.clock = clock;
    }

    /** Body of the run; a failure is logged and swallowed, and what was not recorded stays due. */
    void sweep() {
        try {
            int reminded = sweep(clock.instant());
            if (reminded > 0) log.info("Reminded of {} signature fields of procedures falling due", reminded);
        } catch (Exception e) {
            log.warn("Reminding of procedures falling due failed", e);
        }
    }

    /**
     * Reminds of the open document steps of every procedure falling due.
     *
     * @param now the time the due dates are measured against
     * @return how many fields were reminded of
     */
    int sweep(Instant now) {
        int reminded = 0;
        for (var procedure : repository.findFallingDue(now, now.plus(AHEAD), MAX_PER_RUN)) {
            var dueAt = procedure.dueAt();
            if (dueAt == null || !repository.markDueReminded(procedure.id(), dueAt, now)) continue;
            for (var step : openDocumentSteps(procedure.id())) {
                var generationId = step.generationId();
                if (generationId == null) continue;
                try {
                    reminded += signing.remind(generationId, now.minus(SETTLED));
                } catch (RuntimeException e) {
                    log.warn("Could not remind of document step {} of procedure {}", step.id(), procedure.id(), e);
                }
            }
        }
        return reminded;
    }

    private List<ProcedureItem> openDocumentSteps(int procedureId) {
        return repository.findItems(procedureId).stream()
                .filter(step ->
                        step.kind() == ProcedureStepKind.DOCUMENT && step.generationId() != null && !step.checked())
                .toList();
    }

    @Override
    public List<ScheduledTask> scheduledTasks() {
        return List.of(new ScheduledTask(
                "procedure-due-reminders", Schedule.fixedDelay(START_DELAY, SWEEP_INTERVAL), this::sweep));
    }
}
