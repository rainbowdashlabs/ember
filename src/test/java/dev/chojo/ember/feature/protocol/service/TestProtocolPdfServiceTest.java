/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.protocol.service;

import dev.chojo.ember.repository.RepositoryTestBase;
import dev.chojo.ember.util.PdfText;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Renders real protocol sheets through Typst and reads the text back out of the PDF, so what is
 * checked is what a reader would see.
 */
class TestProtocolPdfServiceTest extends RepositoryTestBase {
    private static final String TRICKY = "A $x$ \"q\" @ref <lbl> `code` // tail";

    private static TestProtocolPdfService pdfService;

    @BeforeAll
    static void setup() {
        try {
            new ProcessBuilder(System.getenv().getOrDefault("TYPST_BIN", "typst"), "--version")
                    .start()
                    .waitFor();
        } catch (Exception e) {
            assumeTrue(false, "typst binary not available, skipping PDF test");
        }
        pdfService = new TestProtocolPdfService(
                testProtocolRepo, stationMemberRepo, accountRepo, stationRepo, newStationLogoService());
    }

    private record Run(int runId, int memberId) {}

    private Run runWithTrickyNames(String locale) {
        var station = stationRepo.create("Protocol PDF " + locale);
        stationRepo.updateLocale(station.id(), locale);
        var account = accountRepo.create("protocol-pdf-" + UUID.randomUUID() + "@test.com", "Member", TRICKY);
        var member = stationMemberRepo.create(station.id(), account.id());

        var protocol = testProtocolRepo.createProtocol(station.id(), "Protocol " + TRICKY, "", null);
        var section = testProtocolRepo.createSection(protocol.id(), null, "Section " + TRICKY, "", null, null, 0);
        var subsection =
                testProtocolRepo.createSection(protocol.id(), section.id(), "Sub " + TRICKY, "", null, null, 0);
        var passed = testProtocolRepo.createItem(section.id(), "Item " + TRICKY, "", 1.5, 0);
        testProtocolRepo.createItem(section.id(), "Missed", "", 1, 1);
        testProtocolRepo.createItem(subsection.id(), "Nested " + TRICKY, "", 2, 0);
        var deep = testProtocolRepo.createSection(protocol.id(), subsection.id(), "Deep level", "", null, null, 0);
        testProtocolRepo.createItem(deep.id(), "Extra credit", "", 1, 0, true);

        var run =
                testProtocolRepo.createRun(protocol.id(), station.id(), "Run", LocalDate.of(2026, 9, 30), member.id());
        var runMember = testProtocolRepo.addRunMember(run.id(), member.id());
        testProtocolRepo.upsertCheck(runMember.id(), passed.id(), true, member.id());
        testProtocolRepo.completeMember(runMember.id(), 1.5);
        return new Run(run.id(), member.id());
    }

    private static void assertSurvives(String text, String prefix) {
        assertTrue(
                text.replaceAll("\\s+", " ").contains(prefix + " " + TRICKY),
                () -> "'" + prefix + " " + TRICKY + "' is missing from:\n" + text);
    }

    @Test
    void theMemberSheetPrintsMarkupCharactersAsText() {
        var run = runWithTrickyNames("de-DE");

        String text = PdfText.extract(pdfService.exportRunMember(
                run.runId(), run.memberId(), "Protocol " + TRICKY, LocalDate.of(2026, 9, 30)));

        assertNotNull(text, "the sheet must be a readable PDF");
        assertSurvives(text, "Protocol");
        assertSurvives(text, "Section");
        assertSurvives(text, "Sub");
        assertSurvives(text, "Item");
        assertSurvives(text, "Nested");
        assertSurvives(text, "Member");
        assertTrue(text.contains("Deep level"), "a third level is printed");
        assertTrue(text.contains("Extra credit"), "a point three levels down is printed");
        assertTrue(text.replaceAll("\\s+", "").contains("+1P"), "a bonus point is marked");
        assertTrue(text.replaceAll("\\s+", "").contains("1,5/4,5P"), "the bonus point leaves the maximum alone");
        assertTrue(text.contains("Prüfer:"));
        assertTrue(text.contains("Unterschrift:"));
        assertTrue(text.contains("1,5"), "points follow the station's locale");
    }

    @Test
    void theEvaluationTablePrintsMarkupCharactersAsText() {
        var run = runWithTrickyNames("de-DE");

        String text = PdfText.extract(
                pdfService.exportEvaluationTable(run.runId(), "Protocol " + TRICKY, LocalDate.of(2026, 9, 30)));

        assertNotNull(text, "the table must be a readable PDF");
        assertSurvives(text, "Protocol");
        assertTrue(text.contains("Auswertung"));
        assertTrue(text.contains("Thema"));
        assertTrue(text.contains("Gesamt"));
        assertTrue(text.replaceAll("\\s+", " ").contains("$x$"), "the section names survive in the table");
        assertTrue(text.contains("Deep level"), "a third level gets its own row");
    }

    @Test
    void anEnglishStationGetsEnglishSheets() {
        var run = runWithTrickyNames("en-GB");

        String member = PdfText.extract(pdfService.exportRunMember(
                run.runId(), run.memberId(), "Protocol " + TRICKY, LocalDate.of(2026, 9, 30)));
        String evaluation = PdfText.extract(
                pdfService.exportEvaluationTable(run.runId(), "Protocol " + TRICKY, LocalDate.of(2026, 9, 30)));

        assertNotNull(member);
        assertNotNull(evaluation);
        assertTrue(member.contains("Examiner:"));
        assertTrue(member.contains("Signature:"));
        assertTrue(member.contains("1.5"), "points follow the station's locale");
        assertFalse(member.contains("Unterschrift"));
        assertTrue(evaluation.contains("Evaluation"));
        assertTrue(evaluation.contains("Topic"));
        assertFalse(evaluation.contains("Auswertung"));
        assertSurvives(member, "Item");
    }
}
