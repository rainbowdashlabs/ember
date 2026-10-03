/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.service;

import dev.chojo.ember.api.refusal.DocumentRefusal;
import dev.chojo.ember.api.refusal.RefusalDetail;
import dev.chojo.ember.api.refusal.RefusalResponse;
import dev.chojo.ember.feature.generator.entity.DocumentTemplate;
import dev.chojo.ember.feature.generator.entity.GenerationContext;
import dev.chojo.ember.feature.generator.entity.GenerationJob;
import dev.chojo.ember.feature.generator.entity.GenerationOrigin;
import dev.chojo.ember.feature.generator.entity.MissingValue;
import dev.chojo.ember.feature.generator.repository.DocumentTemplateRepository;
import dev.chojo.ember.feature.generator.repository.GenerationJobRepository;
import dev.chojo.ember.lifecycle.Schedule;
import dev.chojo.ember.lifecycle.ScheduledTask;
import dev.chojo.ember.lifecycle.SerialLane;
import dev.chojo.ember.lifecycle.TaskScheduler;
import dev.chojo.ember.lifecycle.TaskSource;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Works through generation runs in the background, one run at a time, so a run of many members never
 * holds a request and two runs never draw at once.
 *
 * <p>Everything a run needs lives in the database: each member is marked filed or failed as soon as it
 * is done, and a run picked up again generates only the members still waiting. A run is handed in when
 * it is started, and a periodic task hands in every run that is not finished and not already queued,
 * which is what carries a run on after a restart.
 *
 * <p>A member fails on their own and the run goes on: missing data where the run does not file gaps, a
 * letter that asks one person to sign twice, an archived template, and anything that goes wrong while
 * drawing or filing. The cooldown of self service does not apply, as for any document a manager files.
 */
@Singleton
public class GenerationJobRunner implements TaskSource {
    private static final Logger log = LoggerFactory.getLogger(GenerationJobRunner.class);
    private static final Duration RESUME_INTERVAL = Duration.ofMinutes(1);

    private final GenerationJobRepository jobs;
    private final DocumentTemplateRepository templates;
    private final DocumentGeneratorService generator;
    private final DocumentGenerationService generation;
    private final SerialLane lane;
    private final Set<Integer> queued = ConcurrentHashMap.newKeySet();

    @Inject
    public GenerationJobRunner(
            GenerationJobRepository jobs,
            DocumentTemplateRepository templates,
            DocumentGeneratorService generator,
            DocumentGenerationService generation,
            TaskScheduler scheduler) {
        this.jobs = jobs;
        this.templates = templates;
        this.generator = generator;
        this.generation = generation;
        this.lane = scheduler.lane("document-generation-job");
    }

    @Override
    public List<ScheduledTask> scheduledTasks() {
        return List.of(new ScheduledTask(
                "document-generation-job-resume",
                Schedule.fixedDelay(Duration.ofSeconds(30), RESUME_INTERVAL),
                this::resumeUnfinished));
    }

    /**
     * Queues a run behind the ones already waiting, unless it is queued already.
     *
     * @param jobId the run
     */
    public void submit(int jobId) {
        if (!queued.add(jobId)) return;
        if (!lane.submit(() -> run(jobId))) queued.remove(jobId);
    }

    /** Queues every run that is not finished, which after a restart is every run that was cut off. */
    void resumeUnfinished() {
        jobs.unfinished().forEach(this::submit);
    }

    /**
     * Generates every member of a run still waiting and closes the run.
     *
     * @param jobId the run
     */
    void run(int jobId) {
        try {
            var job = jobs.find(jobId).orElse(null);
            if (job == null || job.finished()) return;
            var template = templates.findById(job.templateId()).orElse(null);
            if (template != null) work(job, template);
            jobs.finish(jobId);
        } finally {
            queued.remove(jobId);
        }
    }

    private void work(GenerationJob job, DocumentTemplate template) {
        var source = generator.sourceOf(template);
        var context = GenerationContext.by(job.startedBy());
        for (int memberId : jobs.waiting(job.id())) {
            try {
                if (template.archived()) throw DocumentRefusal.DOCUMENT_TEMPLATE_ARCHIVED.raise();
                var prepared = generator.prepare(source, memberId, context);
                if (!job.acceptMissing()) requireComplete(prepared);
                var filed = generation.file(template, memberId, job.startedBy(), GenerationOrigin.MANAGER, prepared);
                jobs.markFiled(job.id(), memberId, filed.generationId());
            } catch (RefusalResponse refused) {
                jobs.markFailed(job.id(), memberId, refused.refusal().code(), detailOf(refused.detail()));
            } catch (RuntimeException e) {
                log.warn("Run {} could not generate template {} for member {}", job.id(), template.id(), memberId, e);
                jobs.markFailed(job.id(), memberId, DocumentRefusal.DOCUMENT_RENDER_FAILED.code(), null);
            }
        }
        log.info("Run {} of template {} done", job.id(), template.id());
    }

    private void requireComplete(DocumentGeneratorService.Prepared prepared) {
        var missing = generator.missing(prepared);
        if (missing.isEmpty()) return;
        String labels = missing.stream().map(MissingValue::label).collect(Collectors.joining(", "));
        throw DocumentRefusal.DOCUMENT_JOB_VALUES_MISSING.raise(RefusalDetail.text(labels));
    }

    /** The value a refusal named, where it is one a reader can be shown as it is. */
    private static @Nullable String detailOf(@Nullable RefusalDetail detail) {
        return detail instanceof RefusalDetail.TextDetail text ? text.text() : null;
    }
}
