/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.repository;

import dev.chojo.ember.feature.generator.entity.DataSubject;
import dev.chojo.ember.feature.generator.entity.DocumentGeneration;
import dev.chojo.ember.feature.generator.entity.DocumentTemplateDraft;
import dev.chojo.ember.feature.generator.entity.LetterContent;
import dev.chojo.ember.feature.generator.entity.SubjectRole;
import dev.chojo.ember.feature.restriction.RestrictionMode;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The generation log and its data subjects, and what the templates write and read beside it.
 */
class DocumentGenerationRepositoryTest extends RepositoryTestBase {
    private static final DocumentGenerationRepository log = new DocumentGenerationRepository();
    private static final DocumentTemplateRepository templates = new DocumentTemplateRepository();

    private static Station station;
    private static int child;
    private static int guardian;
    private static int templateId;

    @BeforeAll
    static void setup() {
        station = stationRepo.create("Generation Log Wache");
        child = stationMemberRepo
                .create(
                        station.id(),
                        accountRepo.create("log-child@test.com", "Kind", "Log").id())
                .id();
        guardian = stationMemberRepo
                .create(
                        station.id(),
                        accountRepo
                                .create("log-guardian@test.com", "Eltern", "Log")
                                .id())
                .id();
        templateId = templates.create(station.id(), draft("Log"), guardian).id();
        templates.writeLetter(templateId, LetterContent.blank());
    }

    private static DocumentTemplateDraft draft(String name) {
        return new DocumentTemplateDraft(
                name,
                name,
                name,
                List.of("a"),
                false,
                false,
                false,
                false,
                30,
                RestrictionMode.AND,
                null,
                LetterContent.blank());
    }

    private static DocumentGeneration entry(boolean selfService) {
        return new DocumentGeneration(
                0,
                station.id(),
                templateId,
                3,
                child,
                guardian,
                Instant.EPOCH,
                selfService,
                null,
                "ab".repeat(32),
                null);
    }

    @Test
    void anEntryKeepsItsSubjectsTheMemberFirst() {
        var written = log.log(
                entry(false),
                List.of(new DataSubject(guardian, SubjectRole.GUARDIAN), new DataSubject(child, SubjectRole.MEMBER)));

        assertEquals(written, log.findById(written.id()).orElseThrow());
        assertEquals(3, written.templateVersion());
        assertEquals(
                List.of(new DataSubject(child, SubjectRole.MEMBER), new DataSubject(guardian, SubjectRole.GUARDIAN)),
                log.subjects(written.id()));
    }

    /** Only what was generated through self service counts towards the wait. */
    @Test
    void theLastSelfServiceGenerationIsWhatTheWaitCountsFrom() {
        var otherTemplate =
                templates.create(station.id(), draft("Wartezeit"), guardian).id();
        assertNull(log.lastSelfService(otherTemplate, child));

        log.log(
                new DocumentGeneration(
                        0, station.id(), otherTemplate, 1, child, child, Instant.EPOCH, false, null, "00", null),
                List.of());
        assertNull(log.lastSelfService(otherTemplate, child));

        var bySelf = log.log(
                new DocumentGeneration(
                        0, station.id(), otherTemplate, 1, child, child, Instant.EPOCH, true, null, "01", null),
                List.of());
        assertEquals(bySelf.generatedAt(), log.lastSelfService(otherTemplate, child));
    }

    @Test
    void aTemplateWithoutALetterHasNoneToRead() {
        assertTrue(templates.findLetter(-1).isEmpty());
        assertTrue(templates.update(-1, draft("Nichts"), guardian).isEmpty());
        assertEquals(LetterContent.blank(), templates.findLetter(templateId).orElseThrow());
        assertEquals(List.of("a"), templates.findById(templateId).orElseThrow().tags());
    }
}
