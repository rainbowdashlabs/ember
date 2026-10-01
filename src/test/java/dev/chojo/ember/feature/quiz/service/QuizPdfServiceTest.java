/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.quiz.service;

import dev.chojo.ember.feature.media.service.ImageVariants;
import dev.chojo.ember.feature.quiz.entity.CatalogMetadata;
import dev.chojo.ember.feature.quiz.entity.QuizQuestionType;
import dev.chojo.ember.feature.restriction.service.RestrictionService;
import dev.chojo.ember.feature.storage.backend.StorageBackendResolver;
import dev.chojo.ember.feature.storage.service.StorageService;
import dev.chojo.ember.repository.RepositoryTestBase;
import dev.chojo.ember.util.Json;
import dev.chojo.ember.util.PdfText;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.mock;

/**
 * Renders real sheets through Typst and reads the text back out of the PDF, so what is checked is
 * what a reader would see.
 */
class QuizPdfServiceTest extends RepositoryTestBase {
    private static final List<String> MARKUP = List.of("$x$", "\"q\"", "@ref", "<lbl>", "`code`", "// tail");
    private static final String TRICKY = "A " + String.join(" ", MARKUP);

    private static QuizPdfService pdfService;
    private static QuizTestService testService;

    @BeforeAll
    static void setup() {
        try {
            new ProcessBuilder(System.getenv().getOrDefault("TYPST_BIN", "typst"), "--version")
                    .start()
                    .waitFor();
        } catch (Exception e) {
            assumeTrue(false, "typst binary not available, skipping PDF test");
        }
        testService = new QuizTestService(
                quizTestRepo, new QuizQuestionSelector(quizCatalogRepo, quizTestRepo), mock(RestrictionService.class));
        var backend = localStorage();
        var storage = new StorageService(new StorageBackendResolver(backend), backend);
        pdfService = new QuizPdfService(
                quizTestRepo,
                quizCatalogRepo,
                new QuizQuestionImageService(new ImageVariants(storage), stationRepo),
                stationRepo);
    }

    private static String json(Object value) {
        return Json.MAPPER.writeValueAsString(value);
    }

    private int testWithEveryQuestionType(String locale) {
        var station = stationRepo.create("Quiz PDF " + locale);
        stationRepo.updateLocale(station.id(), locale);
        var catalog = quizCatalogRepo.create(station.id(), TRICKY, "", false, CatalogMetadata.none());
        var category = quizCatalogRepo.createCategory(station.id(), "General", "", 0);
        var questions = List.of(
                Map.entry(
                        QuizQuestionType.MULTIPLE_CHOICE,
                        json(Map.of(
                                "options",
                                List.of(
                                        Map.of("text", "Choice " + TRICKY, "correct", true),
                                        Map.of("text", "Other", "correct", false))))),
                Map.entry(QuizQuestionType.TRUE_FALSE, json(Map.of("correctAnswer", true))),
                Map.entry(
                        QuizQuestionType.FILL_IN_THE_BLANK,
                        json(Map.of("text", "Gap ___ in " + TRICKY, "answers", List.of("Fill " + TRICKY)))),
                Map.entry(
                        QuizQuestionType.FILL_IN_THE_BLANK,
                        json(Map.of(
                                "text",
                                "Bank ___ text",
                                "answers",
                                List.of("Helm"),
                                "distractors",
                                List.of("Word " + TRICKY)))),
                Map.entry(
                        QuizQuestionType.CONNECT,
                        json(Map.of("pairs", List.of(Map.of("left", "Left " + TRICKY, "right", "Right " + TRICKY))))),
                Map.entry(QuizQuestionType.ORDERING, json(Map.of("items", List.of("Step " + TRICKY, "Last")))),
                Map.entry(QuizQuestionType.FREE_ANSWER, json(Map.of("lines", 2, "answers", List.of("Free " + TRICKY)))),
                Map.entry(QuizQuestionType.IMAGE_TEXT, json(Map.of("answer", "Seen " + TRICKY))),
                Map.entry(
                        QuizQuestionType.ENUMERATION,
                        json(Map.of(
                                "answers", List.of("Named " + TRICKY), "requiredCount", 1, "orderedRequired", true))));
        for (int i = 0; i < questions.size(); i++) {
            var question = questions.get(i);
            quizCatalogRepo.createQuestion(
                    catalog.id(),
                    category.id(),
                    question.getKey(),
                    "Q" + i + " " + TRICKY,
                    "Hint " + TRICKY,
                    null,
                    i % 2 == 0 ? 1.5 : 2,
                    false,
                    question.getValue(),
                    i);
        }
        var test = quizTestRepo.create(station.id(), "Title " + TRICKY, "", 20, false, false, 0);
        var section = quizTestRepo.createSection(test.id(), "Section " + TRICKY, "", 0);
        quizTestRepo.createSource(section.id(), catalog.id(), category.id(), questions.size());
        testService.activateTest(test.id());
        return test.id();
    }

    private static void assertMarkupSurvives(String text, String prefix) {
        assertTrue(
                text.replaceAll("\\s+", " ").contains(prefix + " " + TRICKY),
                () -> "'" + prefix + " " + TRICKY + "' is missing from:\n" + text);
    }

    @Test
    void theQuestionSheetPrintsMarkupCharactersAsText() throws Exception {
        int testId = testWithEveryQuestionType("de-DE");

        var sheet = pdfService.exportQuestionPdf(testId);
        String text = PdfText.extract(sheet.bytes());

        assertNotNull(text, "the sheet must be a readable PDF");
        assertMarkupSurvives(text, "Title");
        assertMarkupSurvives(text, "Section");
        assertMarkupSurvives(text, "Hint");
        assertMarkupSurvives(text, "Choice");
        assertMarkupSurvives(text, "Word");
        assertMarkupSurvives(text, "Step");
        assertTrue(text.contains("Datum:"));
        assertTrue(text.contains("Abschnitt:"));
        assertFalse(text.contains("Fill " + TRICKY), "the question sheet must not give the answers away");
    }

    @Test
    void theSolutionSheetPrintsMarkupCharactersAsText() throws Exception {
        int testId = testWithEveryQuestionType("de-DE");

        String text = PdfText.extract(pdfService.exportSolutionPdf(testId).bytes());

        assertNotNull(text);
        assertMarkupSurvives(text, "Fill");
        assertMarkupSurvives(text, "Left");
        assertMarkupSurvives(text, "Right");
        assertMarkupSurvives(text, "Free");
        assertMarkupSurvives(text, "Seen");
        assertMarkupSurvives(text, "Named");
        assertTrue(text.contains("Lösungen"));
        assertTrue(text.contains("Reihenfolge ist relevant."));
        assertTrue(text.contains("1,5"), "points follow the station's locale");
    }

    @Test
    void anEnglishStationGetsEnglishSheets() throws Exception {
        int testId = testWithEveryQuestionType("en-US");

        String questions = PdfText.extract(pdfService.exportQuestionPdf(testId).bytes());
        String solutions = PdfText.extract(pdfService.exportSolutionPdf(testId).bytes());

        assertNotNull(questions);
        assertNotNull(solutions);
        assertTrue(questions.contains("Date:"));
        assertTrue(questions.contains("Section:"));
        assertFalse(questions.contains("Datum:"));
        assertTrue(solutions.contains("Solutions"));
        assertTrue(solutions.contains("The order matters."));
        assertFalse(solutions.contains("Lösungen"));
        assertTrue(solutions.contains("1.5"), "points follow the station's locale");
        assertMarkupSurvives(solutions, "Fill");
    }
}
