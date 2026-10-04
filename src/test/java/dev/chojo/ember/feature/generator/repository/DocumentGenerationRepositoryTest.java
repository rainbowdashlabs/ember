/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.repository;

import dev.chojo.ember.feature.content.entity.CellConfig;
import dev.chojo.ember.feature.content.entity.CellContentType;
import dev.chojo.ember.feature.content.entity.ContentCell;
import dev.chojo.ember.feature.content.entity.ContentRow;
import dev.chojo.ember.feature.generator.entity.DataSubject;
import dev.chojo.ember.feature.generator.entity.DocumentGeneration;
import dev.chojo.ember.feature.generator.entity.DocumentLanguage;
import dev.chojo.ember.feature.generator.entity.DocumentTemplateDraft;
import dev.chojo.ember.feature.generator.entity.GenerationLogEntry;
import dev.chojo.ember.feature.generator.entity.LetterContent;
import dev.chojo.ember.feature.generator.entity.LetterPage;
import dev.chojo.ember.feature.generator.entity.SubjectRole;
import dev.chojo.ember.feature.restriction.RestrictionMode;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.owner.Owner;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
    private static int author;
    private static Owner.Station owner;
    private static int templateId;

    @BeforeAll
    static void setup() {
        station = stationRepo.create("Generation Log Wache");
        child = stationMemberRepo
                .create(
                        station.id(),
                        accountRepo.create("log-child@test.com", "Kind", "Log").id())
                .id();
        author = accountRepo.create("log-guardian@test.com", "Eltern", "Log").id();
        guardian = stationMemberRepo.create(station.id(), author).id();
        owner = new Owner.Station(station.id());
        templateId = templates.create(owner, draft("Log"), author).id();
        templates.writeLetter(templateId, LetterContent.blank());
    }

    private static DocumentTemplateDraft draft(String name) {
        return draft(name, null);
    }

    private static DocumentTemplateDraft draft(String name, @Nullable Integer issuer) {
        return new DocumentTemplateDraft(
                name,
                name,
                name,
                List.of("a"),
                false,
                false,
                false,
                false,
                false,
                30,
                RestrictionMode.AND,
                DocumentLanguage.EN,
                issuer,
                issuer == null ? null : "Jugendwart",
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
                null,
                null,
                null,
                guardian,
                "Jugendwart",
                true,
                true);
    }

    /**
     * A deleted member leaves the template that names them as its issuer without one, its function kept,
     * and the documents they issued with nobody named, so the template shows the issuer as missing and the
     * log keeps what it can.
     */
    @Test
    void aDeletedIssuerLeavesTheTemplateAndTheLogWithoutOne() {
        int issuer = stationMemberRepo
                .create(
                        station.id(),
                        accountRepo
                                .create("log-issuer@test.com", "Erika", "Wehr")
                                .id())
                .id();
        int issued =
                templates.create(owner, draft("Ausgestellt", issuer), author).id();
        var entry = entry(false);
        var written = log.log(
                new DocumentGeneration(
                        0,
                        entry.stationId(),
                        issued,
                        1,
                        child,
                        guardian,
                        Instant.EPOCH,
                        false,
                        null,
                        entry.fileSha256(),
                        null,
                        null,
                        null,
                        issuer,
                        "Jugendwart",
                        false,
                        true),
                List.of());
        assertEquals(issuer, templates.findById(issued).orElseThrow().issuerId());

        stationMemberRepo.delete(issuer);

        var template = templates.findById(issued).orElseThrow();
        assertNull(template.issuerId());
        assertEquals("Jugendwart", template.issuerFunction());
        var kept = log.findById(written.id()).orElseThrow();
        assertNull(kept.issuerId());
        assertEquals("Jugendwart", kept.issuerFunction());
        assertTrue(kept.issuerSigns());
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
        var otherTemplate = templates.create(owner, draft("Wartezeit"), author).id();
        assertNull(log.lastSelfService(otherTemplate, child));

        log.log(
                new DocumentGeneration(
                        0,
                        station.id(),
                        otherTemplate,
                        1,
                        child,
                        child,
                        Instant.EPOCH,
                        false,
                        null,
                        "00",
                        null,
                        null,
                        null,
                        null,
                        null,
                        false,
                        false),
                List.of());
        assertNull(log.lastSelfService(otherTemplate, child));

        var bySelf = log.log(
                new DocumentGeneration(
                        0,
                        station.id(),
                        otherTemplate,
                        1,
                        child,
                        child,
                        Instant.EPOCH,
                        true,
                        null,
                        "01",
                        null,
                        null,
                        null,
                        null,
                        null,
                        false,
                        false),
                List.of());
        assertEquals(bySelf.generatedAt(), log.lastSelfService(otherTemplate, child));

        var neverUsed = templates.create(owner, draft("Nie benutzt"), author).id();
        var together = log.lastSelfService(List.of(otherTemplate, neverUsed), child);
        assertEquals(Map.of(otherTemplate, bySelf.generatedAt()), together);
        assertEquals(Map.of(), log.lastSelfService(List.of(), child));
    }

    /** The list of generated documents reads a station's own entries, the newest first, named by template. */
    @Test
    void aStationsLogListsItsOwnEntriesTheNewestFirst() {
        var other = stationRepo.create("Generation Log Nachbarwache");
        var older = log.log(entryAt(station.id(), Instant.parse("2026-01-01T10:00:00Z")), List.of());
        var newer = log.log(entryAt(station.id(), Instant.parse("2026-02-01T10:00:00Z")), List.of());
        log.log(entryAt(other.id(), Instant.parse("2026-03-01T10:00:00Z")), List.of());

        var listed = log.forStation(station.id()).stream()
                .filter(entry -> entry.id() == older.id() || entry.id() == newer.id())
                .toList();

        assertEquals(
                List.of(newer.id(), older.id()),
                listed.stream().map(GenerationLogEntry::id).toList());
        var first = listed.getFirst();
        assertEquals("Log", first.templateName());
        assertEquals(3, first.templateVersion());
        assertEquals(child, first.memberId());
        assertEquals(guardian, first.generatedBy());
        assertFalse(first.ofAssociation());
        assertTrue(log.forStation(other.id()).stream().noneMatch(entry -> entry.id() == older.id()));
    }

    private static DocumentGeneration entryAt(int stationId, Instant at) {
        return new DocumentGeneration(
                0,
                stationId,
                templateId,
                3,
                child,
                guardian,
                at,
                false,
                null,
                "cd".repeat(32),
                null,
                null,
                null,
                null,
                null,
                false,
                false);
    }

    @Test
    void aTemplateWithoutALetterHasNoneToRead() {
        assertTrue(templates.findLetter(-1).isEmpty());
        assertTrue(templates.update(-1, draft("Nichts"), guardian).isEmpty());
        assertEquals(LetterContent.blank(), templates.findLetter(templateId).orElseThrow());
        assertEquals(List.of("a"), templates.findById(templateId).orElseThrow().tags());
        assertEquals(
                DocumentLanguage.EN,
                templates.findById(templateId).orElseThrow().language());
    }

    @Test
    void aLetterKeepsItsRowsAsWritten() {
        var cell = new ContentCell(0, 0, 0, 50.0, CellContentType.MARKDOWN, "Hallo", CellConfig.EMPTY);
        var row = new ContentRow(0, 0, 0, List.of(cell));
        var second = new ContentRow(0, 0, 1, List.of(cell));
        var letter = new LetterContent(List.of(row), List.of(), List.of(row, second), LetterPage.defaults());
        int letterId = templates.create(owner, draft("Zeilen"), author).id();

        templates.writeLetter(letterId, letter);

        assertEquals(letter, templates.findLetter(letterId).orElseThrow());
    }
}
