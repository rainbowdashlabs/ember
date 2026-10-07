/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.service;

import dev.chojo.ember.api.StationSession;
import dev.chojo.ember.api.refusal.DocumentRefusal;
import dev.chojo.ember.api.refusal.RefusalDetail;
import dev.chojo.ember.api.refusal.RefusalResponse;
import dev.chojo.ember.feature.documents.service.DocumentDoor;
import dev.chojo.ember.feature.documents.service.DocumentService;
import dev.chojo.ember.feature.generator.entity.GenerationContext;
import dev.chojo.ember.feature.generator.entity.GenerationJob;
import dev.chojo.ember.feature.generator.entity.GenerationJobMember;
import dev.chojo.ember.feature.generator.entity.JobMemberStatus;
import dev.chojo.ember.feature.generator.entity.MissingValue;
import dev.chojo.ember.feature.generator.repository.GenerationJobRepository;
import dev.chojo.ember.feature.generator.service.DocumentGeneratorService.PreviewResponse;
import dev.chojo.ember.feature.generator.service.DocumentIssuerService.IssuerChoice;
import dev.chojo.ember.feature.members.service.MemberNameResolver;
import dev.chojo.ember.feature.restriction.RestrictionAudience;
import dev.chojo.ember.feature.restriction.service.RestrictionService;
import dev.chojo.ember.util.sql.Transactions;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.Collator;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Generating one template for many members at once, as a manager does from the template list or from a
 * selection of the member list.
 *
 * <p>The members are named one by one or by an audience (groups, user types, tags), which takes in the
 * station's current members it matches. Before a run starts, a preview draws the document of the first
 * member and lists for every member what data is missing or what keeps their document from being drawn,
 * so the manager can decide whether gaps are filed as lines to fill in by hand or keep a member out.
 * The manager may pick another issuer for the whole run; the run keeps the issuer it was started with.
 *
 * <p>The run itself is written to the database and handed to {@link GenerationJobRunner}, so the request
 * answers at once and the run's progress and results can be read again after a reload or a restart.
 */
@Singleton
public class BulkGenerationService {
    private static final Logger log = LoggerFactory.getLogger(BulkGenerationService.class);

    /** The most members one run generates for. */
    static final int MAX_MEMBERS = 1000;

    /** How many runs the list shows. */
    static final int RECENT_RUNS = 10;

    private final DocumentTemplateService templates;
    private final DocumentGeneratorService generator;
    private final GenerationJobRepository jobs;
    private final GenerationJobRunner runner;
    private final RestrictionService restrictions;
    private final MemberNameResolver names;
    private final DocumentService documents;
    private final DocumentIssuerService issuers;

    @Inject
    public BulkGenerationService(
            DocumentTemplateService templates,
            DocumentGeneratorService generator,
            GenerationJobRepository jobs,
            GenerationJobRunner runner,
            RestrictionService restrictions,
            MemberNameResolver names,
            DocumentService documents,
            DocumentIssuerService issuers) {
        this.templates = templates;
        this.generator = generator;
        this.jobs = jobs;
        this.runner = runner;
        this.restrictions = restrictions;
        this.names = names;
        this.documents = documents;
        this.issuers = issuers;
    }

    /**
     * Whom a run generates for: the members named one by one, or where none are, the current members an
     * audience takes in, which without an audience is every current member.
     *
     * @param memberIds the members, in the order they are generated, or null to choose by audience
     * @param audience  the audience, or null for every current member
     */
    public record MemberSelection(
            @Nullable List<Integer> memberIds, @Nullable RestrictionAudience audience) {}

    /**
     * What a member lacks for a template, or what keeps their document from being drawn at all.
     *
     * @param memberId    the member
     * @param name        their name
     * @param missing     the placeholders without a value, which print as gaps
     * @param refusalCode the refusal that keeps the document from being drawn, or null where it can be
     */
    public record MemberGaps(
            int memberId,
            String name,
            List<MissingValue> missing,
            @Nullable String refusalCode) {}

    /**
     * A look at a run before it starts.
     *
     * @param memberCount     how many members the run would generate for
     * @param previewMemberId the member the preview is drawn for, the first one whose document can be
     *                        drawn, or null where none can
     * @param preview         that member's document, or null
     * @param gaps            every member with missing data or a document that cannot be drawn
     */
    public record BulkPreviewResponse(
            int memberCount,
            @Nullable Integer previewMemberId,
            @Nullable PreviewResponse preview,
            List<MemberGaps> gaps) {}

    /**
     * A run as a list shows it.
     *
     * @param id            the run
     * @param templateId    the template
     * @param templateName  what the template is called
     * @param startedByName who started the run
     * @param acceptMissing whether members with incomplete data get a document with gaps
     * @param startedAt     when it was started
     * @param finishedAt    when the last member was done, or null while it runs
     * @param total         how many members it generates for
     * @param filed         how many documents it filed so far
     * @param failed        how many members it could not file a document for
     */
    public record GenerationJobSummary(
            int id,
            int templateId,
            String templateName,
            String startedByName,
            boolean acceptMissing,
            Instant startedAt,
            @Nullable Instant finishedAt,
            int total,
            int filed,
            int failed) {}

    /**
     * How a run went for one member.
     *
     * @param memberId    the member
     * @param name        their name
     * @param status      how far the run got with them
     * @param documentId  the document filed for them, or null
     * @param refusalCode the refusal that kept a document from being filed, or null
     * @param detail      what that refusal was about, or null
     */
    public record JobMemberResult(
            int memberId,
            String name,
            JobMemberStatus status,
            @Nullable Integer documentId,
            @Nullable String refusalCode,
            @Nullable RefusalDetail detail) {}

    /**
     * A run with how it went for each of its members.
     *
     * @param job     the run
     * @param members its members in the order they are generated
     */
    public record GenerationJobResponse(GenerationJobSummary job, List<JobMemberResult> members) {}

    /**
     * Draws the document of the first member of a selection and lists what every member lacks.
     *
     * @param session    the manager
     * @param templateId the template, already checked to be the station's
     * @param selection  whom the run would generate for
     * @param issuer     the member the manager picked to issue the documents, or null for the template's
     * @return the preview
     */
    public BulkPreviewResponse preview(
            StationSession session, int templateId, MemberSelection selection, @Nullable IssuerChoice issuer) {
        var template = templates.requireInUse(session.stationId(), templateId);
        var memberIds = chosen(session.stationId(), selection);
        var context =
                GenerationContext.by(session.member().id(), issuers.forManager(template, session.stationId(), issuer));
        var source = generator.sourceOf(template);
        var batch = generator.batch(memberIds);
        var gaps = new ArrayList<MemberGaps>();
        Integer firstId = null;
        DocumentGeneratorService.Prepared first = null;
        for (int memberId : memberIds) {
            try {
                var prepared = batch.prepare(source, memberId, context);
                if (first == null) {
                    firstId = memberId;
                    first = prepared;
                }
                if (prepared.missing().isEmpty()) continue;
                gaps.add(new MemberGaps(memberId, names.identified(memberId), prepared.missing(), null));
            } catch (RefusalResponse refused) {
                gaps.add(new MemberGaps(
                        memberId,
                        names.identified(memberId),
                        List.of(),
                        refused.refusal().code()));
            }
        }
        var preview = first == null ? null : batch.preview(first);
        return new BulkPreviewResponse(memberIds.size(), firstId, preview, gaps);
    }

    /**
     * Starts a run in the background.
     *
     * @param session       the manager, who is the uploader of every document of the run
     * @param templateId    the template, already checked to be the station's
     * @param selection     whom the run generates for
     * @param acceptMissing whether members with incomplete data get a document with gaps
     * @param issuer        the member the manager picked to issue the documents, or null for the template's
     * @return the run as it starts
     */
    public GenerationJobResponse start(
            StationSession session,
            int templateId,
            MemberSelection selection,
            boolean acceptMissing,
            @Nullable IssuerChoice issuer) {
        documents.requireKept(session.stationId(), DocumentDoor.STATION);
        var template = templates.requireInUse(session.stationId(), templateId);
        var memberIds = chosen(session.stationId(), selection);
        var issuedBy = issuers.forManager(template, session.stationId(), issuer);
        int jobId = Transactions.call(() -> jobs.create(
                session.stationId(), template.id(), session.member().id(), acceptMissing, issuedBy, memberIds));
        log.info("Run {} of template {} started for {} members", jobId, template.id(), memberIds.size());
        runner.submit(jobId);
        return job(session.stationId(), jobId);
    }

    /**
     * The latest runs of the station, the newest first.
     *
     * @param session the manager
     * @return the runs
     */
    public List<GenerationJobSummary> recent(StationSession session) {
        var recent = jobs.recent(session.stationId(), RECENT_RUNS);
        var named =
                names.identified(recent.stream().map(GenerationJob::startedBy).toList());
        return recent.stream().map(job -> summary(job, named)).toList();
    }

    /**
     * A run of the station with how it went for each member.
     *
     * @param stationId the station of the reader
     * @param jobId     the run
     * @return the run
     */
    public GenerationJobResponse job(int stationId, int jobId) {
        var job = jobs.find(jobId)
                .filter(found -> found.stationId() == stationId)
                .orElseThrow(DocumentRefusal.DOCUMENT_JOB_NOT_HERE::raise);
        var members = jobs.members(jobId);
        var ids = new ArrayList<>(
                members.stream().map(GenerationJobMember::memberId).toList());
        ids.add(job.startedBy());
        var named = names.identified(ids);
        return new GenerationJobResponse(
                summary(job, named),
                members.stream().map(member -> result(member, named)).toList());
    }

    /**
     * The members a selection names, each once, refusing a selection naming nobody, too many, or
     * somebody who is no current member of the station.
     */
    private List<Integer> chosen(int stationId, MemberSelection selection) {
        var named = selection.memberIds();
        List<Integer> memberIds;
        if (named != null) {
            var current = new HashSet<>(restrictions.membersIn(stationId, RestrictionAudience.empty()));
            memberIds = named.stream().filter(Objects::nonNull).distinct().toList();
            if (!current.containsAll(memberIds)) throw DocumentRefusal.DOCUMENT_JOB_MEMBER_NOT_HERE.raise();
        } else {
            var audience = Objects.requireNonNullElse(selection.audience(), RestrictionAudience.empty());
            memberIds = byName(restrictions.membersIn(stationId, audience));
        }
        if (memberIds.isEmpty()) throw DocumentRefusal.DOCUMENT_JOB_NO_MEMBERS.raise();
        if (memberIds.size() > MAX_MEMBERS) {
            throw DocumentRefusal.DOCUMENT_JOB_TOO_MANY_MEMBERS.raise(RefusalDetail.count(MAX_MEMBERS));
        }
        return memberIds;
    }

    private List<Integer> byName(List<Integer> memberIds) {
        var collator = Collator.getInstance(Locale.GERMAN);
        var named = names.identified(memberIds);
        return memberIds.stream()
                .sorted(Comparator.comparing(named::get, collator))
                .toList();
    }

    private static GenerationJobSummary summary(GenerationJob job, Map<Integer, String> named) {
        return new GenerationJobSummary(
                job.id(),
                job.templateId(),
                job.templateName(),
                named.get(job.startedBy()),
                job.acceptMissing(),
                job.startedAt(),
                job.finishedAt(),
                job.total(),
                job.filed(),
                job.failed());
    }

    private static JobMemberResult result(GenerationJobMember member, Map<Integer, String> named) {
        String detail = member.refusalDetail();
        return new JobMemberResult(
                member.memberId(),
                named.get(member.memberId()),
                member.status(),
                member.documentId(),
                member.refusalCode(),
                detail == null ? null : RefusalDetail.text(detail));
    }
}
