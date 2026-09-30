/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.quiz.service;

import dev.chojo.ember.feature.quiz.entity.QuizQuestion;
import dev.chojo.ember.feature.quiz.repository.QuizCatalogRepository;
import dev.chojo.ember.feature.quiz.repository.QuizTestRepository;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.entity.StationFormat;
import dev.chojo.ember.feature.station.repository.StationRepository;
import dev.chojo.ember.util.DocumentName;
import dev.chojo.ember.util.DocumentNumber;
import dev.chojo.ember.util.DocumentWord;
import dev.chojo.ember.util.ExportedDocument;
import dev.chojo.ember.util.TypstCompiler;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Prints a test as a sheet, either the questions to hand out or the answers to mark against. The
 * sheet is the {@code quiz-sheet.typ} template in the station's document language, fed with the
 * test as data, so nothing a question says is ever read as markup.
 */
@Singleton
public class QuizPdfService {
    private static final String TEMPLATE = "quiz-sheet.typ";

    private final QuizTestRepository testRepository;
    private final QuizCatalogRepository catalogRepository;
    private final QuizQuestionImageService imageService;
    private final StationRepository stationRepository;

    @Inject
    public QuizPdfService(
            QuizTestRepository testRepository,
            QuizCatalogRepository catalogRepository,
            QuizQuestionImageService imageService,
            StationRepository stationRepository) {
        this.testRepository = testRepository;
        this.catalogRepository = catalogRepository;
        this.imageService = imageService;
        this.stationRepository = stationRepository;
    }

    private static String extensionFor(String contentType) {
        return switch (contentType == null ? "" : contentType.toLowerCase()) {
            case "image/jpeg" -> ".jpg";
            case "image/webp" -> ".webp";
            case "image/gif" -> ".gif";
            default -> ".png";
        };
    }

    public ExportedDocument exportQuestionPdf(int testId) throws IOException, InterruptedException {
        return export(testId, false);
    }

    public ExportedDocument exportSolutionPdf(int testId) throws IOException, InterruptedException {
        return export(testId, true);
    }

    /**
     * A test as a sheet, either the questions to hand out or the answers to mark against.
     *
     * <p>The name carries the test's own, because a folder full of question sheets that all say
     * "questions" tells whoever is printing them nothing at all.
     */
    private ExportedDocument export(int testId, boolean withAnswers) throws IOException, InterruptedException {
        var test = testRepository.findById(testId).orElseThrow();
        Station station = stationRepository.findById(test.stationId()).orElse(null);
        String language = StationFormat.languageOf(station);
        Locale locale = StationFormat.localeOf(station);

        var images = new HashMap<String, byte[]>();
        var sections = new ArrayList<SheetSection>();
        double totalPoints = 0;
        int number = 1;
        for (var section : collectSections(testId)) {
            var questions = new ArrayList<SheetQuestion>();
            double sectionPoints = 0;
            for (var question : section.questions()) {
                sectionPoints += question.points();
                String image = resolveImage(question, number, images);
                questions.add(new SheetQuestion(
                        number++,
                        question.title(),
                        question.description() == null ? "" : question.description(),
                        DocumentNumber.of(question.points(), locale),
                        image,
                        QuizSheetBody.of(question.config(), withAnswers)));
            }
            totalPoints += sectionPoints;
            sections.add(new SheetSection(section.title(), DocumentNumber.of(sectionPoints, locale), questions));
        }

        var data = new LinkedHashMap<String, Object>();
        data.put("title", test.title());
        data.put("withAnswers", withAnswers);
        data.put("totalPoints", DocumentNumber.of(totalPoints, locale));
        data.put("sections", sections);

        String filename = DocumentName.of(
                "pdf",
                DocumentName.part(test.title()),
                (withAnswers ? DocumentWord.ANSWERS : DocumentWord.QUESTIONS).in(language));
        byte[] pdf = TypstCompiler.compileTemplate(data, language + "/" + TEMPLATE, null, Map.of(), images);
        return new ExportedDocument(pdf, filename);
    }

    /**
     * The test's sections in order, each with the frozen questions it drew. A question frozen
     * without a section belongs to the first one.
     */
    private List<SectionQuestions> collectSections(int testId) {
        var sections = testRepository.findSections(testId);
        var questionsBySection = new LinkedHashMap<Integer, List<QuizQuestion>>();
        for (var section : sections) {
            questionsBySection.put(section.id(), new ArrayList<>());
        }
        for (var frozen : testRepository.findFrozenQuestions(testId)) {
            catalogRepository.findQuestionById(frozen.questionId()).ifPresent(question -> {
                int sectionId = frozen.sectionId() != null
                        ? frozen.sectionId()
                        : sections.getFirst().id();
                questionsBySection
                        .computeIfAbsent(sectionId, _ -> new ArrayList<>())
                        .add(question);
            });
        }
        return sections.stream()
                .map(section ->
                        new SectionQuestions(section.title(), questionsBySection.getOrDefault(section.id(), List.of())))
                .toList();
    }

    /**
     * Places the question's picture next to the template and answers with its file name, or
     * {@code null} when the question has none or it cannot be read.
     */
    private String resolveImage(QuizQuestion question, int number, Map<String, byte[]> images) {
        if (question.imageUrl() == null || question.imageUrl().isBlank()) return null;
        var catalog = catalogRepository.findById(question.catalogId()).orElse(null);
        if (catalog == null) return null;
        var image = imageService.read(catalog.stationId(), question.id(), 0).orElse(null);
        if (image == null) return null;
        String filename = "img-" + number + extensionFor(image.contentType());
        images.put(filename, image.data());
        return filename;
    }

    private record SectionQuestions(String title, List<QuizQuestion> questions) {}

    /**
     * A section as the sheet prints it.
     *
     * @param title the section's heading
     * @param points the points the section's questions are worth together, formatted
     * @param questions the questions in the order they are numbered
     */
    public record SheetSection(String title, String points, List<SheetQuestion> questions) {}

    /**
     * A question as the sheet prints it.
     *
     * @param number the question's number, counted across the whole test
     * @param title the question itself
     * @param description the hint printed under it, empty without one
     * @param points the points it is worth, formatted
     * @param image the file name of its picture next to the template, or {@code null}
     * @param body what is printed below the title, by question type
     */
    public record SheetQuestion(
            int number, String title, String description, String points, String image, QuizSheetBody body) {}
}
