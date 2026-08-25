/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.quiz.service;

import dev.chojo.ember.feature.quiz.entity.CatalogMetadata;
import dev.chojo.ember.feature.quiz.entity.CatalogTransfer;
import dev.chojo.ember.feature.quiz.entity.CatalogTransfer.CatalogInfo;
import dev.chojo.ember.feature.quiz.entity.QuizCatalog;
import dev.chojo.ember.feature.quiz.entity.QuizCatalogTemplate;
import dev.chojo.ember.feature.quiz.entity.QuizCategory;
import dev.chojo.ember.feature.quiz.entity.QuizQuestionType;
import dev.chojo.ember.feature.quiz.service.QuizImportService.CsvMappings;
import dev.chojo.ember.feature.quiz.service.QuizImportService.DraftQuestion;
import dev.chojo.ember.util.Json;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The example files are documentation that has to stay true. Reading them back through the
 * importer is what keeps them honest: an example that drifted away from the format fails here
 * rather than teaching somebody the wrong shape.
 */
class QuizCatalogTemplateTest {

    private static final int STATION_ID = 7;

    private QuizCatalogService catalogService;
    private QuizQuestionService questionService;
    private QuizCatalogTransferService transferService;
    private QuizImportService importService;

    @BeforeEach
    void setUp() {
        catalogService = mock(QuizCatalogService.class);
        questionService = mock(QuizQuestionService.class);
        transferService = new QuizCatalogTransferService(catalogService, questionService);
        importService = new QuizImportService();

        when(catalogService.findCategories(anyInt())).thenReturn(List.of());
        when(catalogService.createCategory(anyInt(), anyString(), anyString(), anyInt()))
                .thenAnswer(invocation -> new QuizCategory(100, STATION_ID, invocation.getArgument(1), "", 0));
        when(catalogService.createCatalog(anyInt(), anyString(), anyString(), anyBoolean(), any()))
                .thenAnswer(invocation -> new QuizCatalog(
                        55,
                        STATION_ID,
                        invocation.getArgument(1),
                        invocation.getArgument(2),
                        invocation.getArgument(3),
                        false,
                        invocation.getArgument(4),
                        Instant.EPOCH,
                        Instant.EPOCH));
    }

    @Test
    void everyTemplateIsShipped() {
        for (var template : QuizCatalogTemplate.values()) {
            assertTrue(template.read().length() > 0, template + " is missing from the build");
        }
    }

    @Test
    void theFormatIsResolvedFromTheRequestInEitherCase() {
        assertEquals(QuizCatalogTemplate.JSON, QuizCatalogTemplate.byFormat("json"));
        assertEquals(QuizCatalogTemplate.CSV, QuizCatalogTemplate.byFormat("CSV"));
        assertNull(QuizCatalogTemplate.byFormat("xlsx"));
        assertNull(QuizCatalogTemplate.byFormat(null));
    }

    @Test
    void theCatalogFileTemplateImportsWithoutAComplaint() {
        var transfer = transferService.read(Json.MAPPER.readTree(QuizCatalogTemplate.JSON.read()));

        var outcome = transferService.importInto(STATION_ID, transfer);

        assertEquals(List.of(), outcome.problems(), "the shipped example must import as it stands");
        assertNotNull(outcome.catalog());
    }

    /** The example is meant to show every type, so a type missing from it is a gap in the docs. */
    @Test
    void theCatalogFileTemplateShowsEveryQuestionType() {
        var transfer = transferService.read(Json.MAPPER.readTree(QuizCatalogTemplate.JSON.read()));

        var shown = transfer.questions().stream()
                .map(question -> QuizQuestionType.valueOf(question.quizQuestionType()))
                .distinct()
                .sorted()
                .toList();

        assertEquals(List.of(QuizQuestionType.values()).stream().sorted().toList(), shown);
    }

    @Test
    void theSheetTemplateDraftsAndThenImports() {
        var draft = importService.draft(QuizCatalogTemplate.CSV.read(), templateMappings());

        assertEquals(8, draft.questions().size());
        var transfer = new CatalogTransfer(
                CatalogTransfer.FORMAT_VERSION,
                new CatalogInfo("Aus der Vorlage", "", false, CatalogMetadata.none()),
                draft.categories(),
                draft.questions().stream().map(DraftQuestion::question).toList());

        var outcome = transferService.importInto(STATION_ID, transfer);

        assertEquals(List.of(), outcome.problems(), "the shipped sheet must survive drafting and importing");
    }

    @Test
    void theSheetTemplateShowsEveryQuestionType() {
        var draft = importService.draft(QuizCatalogTemplate.CSV.read(), templateMappings());

        var shown = draft.questions().stream()
                .map(question -> QuizQuestionType.valueOf(question.question().quizQuestionType()))
                .distinct()
                .sorted()
                .toList();

        assertEquals(List.of(QuizQuestionType.values()).stream().sorted().toList(), shown);
    }

    /** The mapping the format panel tells people to use for the example sheet. */
    private static CsvMappings templateMappings() {
        return new CsvMappings(
                "Frage",
                "Antwort",
                "Kategorie",
                "Typ",
                "Punkte",
                "Hinweis",
                "Bild",
                "Falsch",
                "ProAntwort",
                "Anzahl",
                "Geordnet",
                ",",
                ";",
                null);
    }
}
