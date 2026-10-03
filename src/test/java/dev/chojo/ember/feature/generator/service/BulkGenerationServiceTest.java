/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.service;

import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.api.refusal.DocumentRefusal;
import dev.chojo.ember.api.refusal.RefusalDetail;
import dev.chojo.ember.feature.generator.entity.JobMemberStatus;
import dev.chojo.ember.feature.generator.entity.MissingValue;
import dev.chojo.ember.feature.generator.entity.SignatureRole;
import dev.chojo.ember.feature.generator.repository.DocumentTemplateRepository;
import dev.chojo.ember.feature.generator.repository.GenerationJobRepository;
import dev.chojo.ember.feature.generator.service.BulkGenerationService.GenerationJobResponse;
import dev.chojo.ember.feature.generator.service.BulkGenerationService.JobMemberResult;
import dev.chojo.ember.feature.generator.service.BulkGenerationService.MemberSelection;
import dev.chojo.ember.feature.members.entity.ProfileFieldConfig;
import dev.chojo.ember.feature.members.entity.ProfileFieldScope;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.question.FieldType;
import dev.chojo.ember.feature.restriction.RestrictionAudience;
import dev.chojo.ember.feature.restriction.RestrictionMode;
import dev.chojo.ember.lifecycle.Schedule;
import dev.chojo.ember.lifecycle.TaskScheduler;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.node.StringNode;

import java.time.Duration;
import java.util.List;

import static dev.chojo.ember.feature.generator.service.TemplateRequestBuilder.letter;
import static dev.chojo.ember.feature.generator.service.TemplateRequestBuilder.row;
import static dev.chojo.ember.feature.generator.service.TemplateRequestBuilder.signature;
import static dev.chojo.ember.feature.generator.service.TemplateRequestBuilder.text;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Generating one template for many members in a background run: the look before it, the run itself
 * with the members it files and the ones it lists as failed, and a run that carries on after a restart.
 */
class BulkGenerationServiceTest extends GeneratorTestBase {
    private static final Duration PATIENCE = Duration.ofSeconds(90);

    private static Wiring wiring;
    private static GenerationJobRepository jobs;
    private static BulkGenerationService bulk;
    private static StationMember manager;
    private static StationMember anna;
    private static StationMember ben;
    private static StationMember carla;
    private static StationMember stranger;
    private static int school;
    private static int templateId;
    private static int youthGroup;

    @BeforeAll
    static void setup() {
        wiring = wire("Lauf Wache");
        manager = wiring.member("bulk-manager@test.com", "Nora", "Fülling");
        anna = wiring.member("bulk-anna@test.com", "Anna", "Arndt");
        ben = wiring.member("bulk-ben@test.com", "Ben", "Brandt");
        carla = wiring.member("bulk-carla@test.com", "Carla", "Conrad");
        stationMemberRepo.setUserType(carla.id(), StationUserType.GUARDIAN);
        var elsewhere = stationRepo.create("Andere Lauf Wache");
        stranger = stationMemberRepo.create(
                elsewhere.id(),
                accountRepo.create("bulk-stranger@test.com", "Fremd", "Person").id());

        school = profileFieldRepo
                .create(wiring.station().id(), "Schule", FieldType.TEXT, ProfileFieldConfig.empty(), false, false, null)
                .id();
        profileFieldRepo.assignToRole(school, ProfileFieldScope.MEMBER, 0, null, null, null);
        profileFieldRepo.setValue(anna.id(), school, StringNode.valueOf("Grundschule am See"));
        profileFieldRepo.setValue(carla.id(), school, StringNode.valueOf("Gymnasium"));

        youthGroup = memberGroupRepo.create(wiring.station().id(), "Jugend").id();
        for (int index = 0; index < 6; index++) {
            var youth = wiring.member("bulk-youth" + index + "@test.com", "Jugend" + index, "Mitglied");
            memberGroupRepo.addMember(youthGroup, youth.id());
        }

        templateId = template("Teilnahme");

        jobs = new GenerationJobRepository();
        bulk = new BulkGenerationService(
                wiring.templates(),
                wiring.generator(),
                jobs,
                runner(),
                restrictionService,
                memberNameResolver,
                wiring.documents());
    }

    /**
     * A letter naming the member's school, with a second line for the issuer that only guardians get,
     * which asks a guardian to sign twice.
     */
    private static int template(String name) {
        var guardians = new RestrictionAudience(
                List.of(StationUserType.GUARDIAN), List.of(), List.of(), List.of(), RestrictionMode.AND);
        var request = letter(name)
                .body(List.of(
                        row(text("{{member.fullName}} besucht die {{profile.%d}}.".formatted(school))),
                        row(signature(SignatureRole.ISSUER, "Jugendwartin")),
                        row(signature(SignatureRole.ISSUER, "Für Eltern", guardians))))
                .build();
        return wiring.templates().create(wiring.owner(), request, manager.id()).id();
    }

    /** A runner as a fresh start of the application has it, knowing of no run. */
    private static GenerationJobRunner runner() {
        return new GenerationJobRunner(
                jobs, new DocumentTemplateRepository(), wiring.generator(), wiring.generation(), new TaskScheduler());
    }

    private static GenerationJobResponse finished(int jobId) throws InterruptedException {
        long until = System.nanoTime() + PATIENCE.toNanos();
        while (System.nanoTime() < until) {
            var job = bulk.job(wiring.station().id(), jobId);
            if (job.job().finishedAt() != null) return job;
            Thread.sleep(Duration.ofMillis(100));
        }
        throw new AssertionError("Run " + jobId + " did not finish in time");
    }

    private static JobMemberResult resultOf(GenerationJobResponse job, StationMember member) {
        return job.members().stream()
                .filter(result -> result.memberId() == member.id())
                .findFirst()
                .orElseThrow();
    }

    @Test
    void aRunFilesWhatItCanAndListsWhyTheRestFailed() throws InterruptedException {
        var started = bulk.start(
                as(manager, StationPermission.DOCUMENT_EDIT_MEMBER),
                templateId,
                new MemberSelection(List.of(anna.id(), ben.id(), carla.id()), null),
                false);
        assertEquals(3, started.job().total());
        assertEquals(
                List.of(anna.id(), ben.id(), carla.id()),
                started.members().stream().map(JobMemberResult::memberId).toList());

        var job = finished(started.job().id());

        assertEquals(1, job.job().filed());
        assertEquals(2, job.job().failed());
        assertEquals("Nora Fülling", job.job().startedByName());
        var forAnna = resultOf(job, anna);
        assertEquals(JobMemberStatus.FILED, forAnna.status());
        assertNotNull(forAnna.documentId());
        assertTrue(wiring.textOf(forAnna.documentId()).contains("Grundschule am See"));
        var filed = memberDocumentRepo.findById(forAnna.documentId()).orElseThrow();
        assertEquals(manager.id(), filed.uploadedBy());

        var forBen = resultOf(job, ben);
        assertEquals(JobMemberStatus.FAILED, forBen.status());
        assertEquals(DocumentRefusal.DOCUMENT_JOB_VALUES_MISSING.code(), forBen.refusalCode());
        assertEquals(RefusalDetail.text("Schule"), forBen.detail());
        assertNull(forBen.documentId());

        var forCarla = resultOf(job, carla);
        assertEquals(DocumentRefusal.DOCUMENT_SIGNER_TWICE_FOR_MEMBER.code(), forCarla.refusalCode());
        assertNull(forCarla.detail());
    }

    @Test
    void aRunThatAcceptsGapsFilesThemAsWell() throws InterruptedException {
        var started =
                bulk.start(as(manager), templateId, new MemberSelection(List.of(ben.id(), anna.id()), null), true);

        var job = finished(started.job().id());

        assertEquals(2, job.job().filed());
        assertEquals(0, job.job().failed());
        assertTrue(job.job().acceptMissing());
        assertEquals(JobMemberStatus.FILED, resultOf(job, ben).status());
    }

    /** What a run did before the restart stays done; what was still waiting is generated after it. */
    @Test
    void aRunCarriesOnAfterARestart() throws InterruptedException {
        int jobId = jobs.create(wiring.station().id(), templateId, manager.id(), true, List.of(anna.id(), ben.id()));
        jobs.markFailed(jobId, anna.id(), "D-034", null);

        var restarted = runner();
        var task = restarted.scheduledTasks().getFirst();
        assertEquals("document-generation-job-resume", task.name());
        assertEquals(Schedule.fixedDelay(Duration.ofSeconds(30), Duration.ofMinutes(1)), task.schedule());
        task.work().run();

        var job = finished(jobId);
        assertEquals(JobMemberStatus.FAILED, resultOf(job, anna).status());
        assertEquals("D-034", resultOf(job, anna).refusalCode());
        assertEquals(JobMemberStatus.FILED, resultOf(job, ben).status());
        assertFalse(jobs.unfinished().contains(jobId));
    }

    @Test
    void aRunOfAnArchivedTemplateFailsItsMembers() {
        int archived = template("Archiviert");
        int jobId = jobs.create(wiring.station().id(), archived, manager.id(), true, List.of(anna.id()));
        wiring.templates().setArchived(wiring.owner(), archived, true, manager.id());

        runner().run(jobId);

        var job = bulk.job(wiring.station().id(), jobId);
        assertNotNull(job.job().finishedAt());
        assertEquals(
                DocumentRefusal.DOCUMENT_TEMPLATE_ARCHIVED.code(),
                resultOf(job, anna).refusalCode());
        refused(
                DocumentRefusal.DOCUMENT_TEMPLATE_ARCHIVED,
                () -> bulk.start(as(manager), archived, new MemberSelection(List.of(anna.id()), null), true));
    }

    @Test
    void thePreviewDrawsTheFirstMemberAndListsWhatEveryMemberLacks() {
        var preview = bulk.preview(
                as(manager), templateId, new MemberSelection(List.of(carla.id(), ben.id(), anna.id()), null));

        assertEquals(3, preview.memberCount());
        assertEquals(ben.id(), preview.previewMemberId(), "the first member whose document can be drawn");
        assertNotNull(preview.preview());
        assertFalse(preview.preview().pdfBase64().isEmpty());
        assertEquals(2, preview.gaps().size());
        var forCarla = preview.gaps().getFirst();
        assertEquals(carla.id(), forCarla.memberId());
        assertEquals(DocumentRefusal.DOCUMENT_SIGNER_TWICE_FOR_MEMBER.code(), forCarla.refusalCode());
        var forBen = preview.gaps().get(1);
        assertEquals("Ben Brandt", forBen.name());
        assertEquals(
                List.of("Schule"),
                forBen.missing().stream().map(MissingValue::label).toList());
        assertNull(forBen.refusalCode());
    }

    @Test
    void thePreviewOfAnAudienceListsTheGapsOfEveryMemberItTakesIn() {
        var youth = new RestrictionAudience(List.of(), List.of(youthGroup), List.of(), List.of(), RestrictionMode.AND);

        var preview = bulk.preview(as(manager), templateId, new MemberSelection(null, youth));

        assertEquals(6, preview.memberCount());
        assertEquals(6, preview.gaps().size());
        assertTrue(preview.gaps().stream().allMatch(gap -> gap.missing().size() == 1));
        assertEquals("Jugend0 Mitglied", preview.gaps().getFirst().name(), "chosen by audience, the run goes by name");
    }

    @Test
    void aSelectionNamingNobodyOrStrangersIsRefused() {
        var session = as(manager);
        refused(
                DocumentRefusal.DOCUMENT_JOB_NO_MEMBERS,
                () -> bulk.preview(session, templateId, new MemberSelection(List.of(), null)));
        refused(
                DocumentRefusal.DOCUMENT_JOB_MEMBER_NOT_HERE,
                () -> bulk.start(session, templateId, new MemberSelection(List.of(stranger.id()), null), true));
        int emptyGroup = memberGroupRepo.create(wiring.station().id(), "Leer").id();
        var nobody = new RestrictionAudience(List.of(), List.of(emptyGroup), List.of(), List.of(), RestrictionMode.AND);
        refused(
                DocumentRefusal.DOCUMENT_JOB_NO_MEMBERS,
                () -> bulk.preview(session, templateId, new MemberSelection(null, nobody)));
    }

    @Test
    void aRunIsReadOnlyAtItsOwnStationAndListedNewestFirst() {
        int older = jobs.create(wiring.station().id(), templateId, manager.id(), true, List.of(anna.id()));
        int newer = jobs.create(wiring.station().id(), templateId, manager.id(), false, List.of(ben.id()));

        var recent = bulk.recent(as(manager));
        var ids = recent.stream()
                .map(BulkGenerationService.GenerationJobSummary::id)
                .toList();
        assertTrue(ids.indexOf(newer) < ids.indexOf(older));
        assertEquals("Teilnahme", recent.getFirst().templateName());

        refused(DocumentRefusal.DOCUMENT_JOB_NOT_HERE, () -> bulk.job(stranger.stationId(), older));
        refused(
                DocumentRefusal.DOCUMENT_JOB_NOT_HERE,
                () -> bulk.job(wiring.station().id(), -1));
    }
}
