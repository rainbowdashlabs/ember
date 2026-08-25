/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.quiz.service;

import dev.chojo.ember.feature.quiz.entity.CatalogMetadata;
import dev.chojo.ember.feature.quiz.entity.CatalogTransfer;
import dev.chojo.ember.feature.quiz.entity.CreateQuestionCommand;
import dev.chojo.ember.feature.quiz.entity.QuestionConfig;
import dev.chojo.ember.feature.quiz.entity.QuizCatalog;
import dev.chojo.ember.feature.quiz.entity.QuizCategory;
import dev.chojo.ember.feature.quiz.entity.QuizQuestion;
import dev.chojo.ember.feature.quiz.entity.QuizQuestionType;
import dev.chojo.ember.util.Json;
import io.javalin.http.BadRequestResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class QuizCatalogTransferServiceTest {

    private static final int STATION_ID = 7;

    private QuizCatalogService catalogService;
    private QuizQuestionService questionService;
    private QuizCatalogTransferService service;
    private int nextCategoryId;

    @BeforeEach
    void setUp() {
        catalogService = mock(QuizCatalogService.class);
        questionService = mock(QuizQuestionService.class);
        service = new QuizCatalogTransferService(catalogService, questionService);
        nextCategoryId = 100;

        when(catalogService.findCategories(anyInt())).thenReturn(List.of());
        when(catalogService.createCategory(anyInt(), anyString(), anyString(), anyInt()))
                .thenAnswer(invocation -> new QuizCategory(
                        nextCategoryId++,
                        invocation.getArgument(0),
                        invocation.getArgument(1),
                        invocation.getArgument(2),
                        invocation.getArgument(3)));
        when(catalogService.createCatalog(anyInt(), anyString(), anyString(), anyBoolean(), any()))
                .thenAnswer(invocation -> catalog(
                        55,
                        invocation.getArgument(0),
                        invocation.getArgument(1),
                        invocation.getArgument(2),
                        invocation.getArgument(4)));
    }

    private static QuizCatalog catalog(
            int id, int stationId, String name, String description, CatalogMetadata metadata) {
        return new QuizCatalog(id, stationId, name, description, false, false, metadata, Instant.EPOCH, Instant.EPOCH);
    }

    private static QuizQuestion question(int id, QuizQuestionType type, String title, int position) {
        return new QuizQuestion(
                id,
                5,
                null,
                type,
                title,
                "",
                null,
                1,
                true,
                new QuestionConfig.TrueFalse(true),
                position,
                Instant.EPOCH,
                Instant.EPOCH);
    }

    private static JsonNode json(String raw) {
        return Json.MAPPER.readTree(raw);
    }

    private List<CreateQuestionCommand> captureCommands(int expected) {
        var captor = ArgumentCaptor.forClass(CreateQuestionCommand.class);
        verify(questionService, times(expected)).createQuestion(captor.capture());
        return captor.getAllValues();
    }

    // -- Reading --

    @Test
    void readsTheCurrentShape() {
        var transfer = service.read(json("""
                {
                  "formatVersion": 1,
                  "catalog": {
                    "name": "Grundwissen",
                    "description": "Basis",
                    "trainingEnabled": true,
                    "metadata": {"language": "de", "author": "Wache 1"}
                  },
                  "categories": [{"key": "brandlehre", "name": "Brandlehre", "description": "", "position": 0}],
                  "questions": [{
                    "categoryKey": "brandlehre",
                    "quizQuestionType": "TRUE_FALSE",
                    "title": "Wasser löscht Fettbrände",
                    "config": {"correctAnswer": false}
                  }]
                }"""));

        assertEquals(1, transfer.formatVersion());
        assertEquals("Grundwissen", transfer.catalog().name());
        assertTrue(transfer.catalog().trainingEnabled());
        assertEquals("de", transfer.catalog().metadata().language());
        assertEquals("Wache 1", transfer.catalog().metadata().author());
        assertEquals("brandlehre", transfer.categories().getFirst().key());
        assertEquals("brandlehre", transfer.questions().getFirst().categoryKey());
    }

    /**
     * Files exported by earlier versions carry the catalog at the top level and address categories
     * by the database id of the station that wrote them. That id becomes the key here, so the
     * questions of the same file still find their category.
     */
    @Test
    void readsTheShapeEarlierVersionsWrote() {
        var transfer = service.read(json("""
                {
                  "name": "Altbestand",
                  "description": "Aus einer früheren Version",
                  "trainingEnabled": false,
                  "categories": [{"id": 12, "stationId": 3, "name": "Knoten", "description": "", "position": 2}],
                  "questions": [{
                    "id": 900,
                    "catalogId": 5,
                    "categoryId": 12,
                    "quizQuestionType": "ENUMERATION",
                    "title": "Nenne drei Knoten",
                    "description": "",
                    "points": 3.0,
                    "autoPoints": true,
                    "config": {"answers": ["Mastwurf", "Ankerstich", "Achterknoten"], "requiredCount": 3},
                    "position": 4
                  }]
                }"""));

        assertEquals("Altbestand", transfer.catalog().name());
        assertEquals("12", transfer.categories().getFirst().key());
        assertEquals("12", transfer.questions().getFirst().categoryKey());
        assertEquals(4, transfer.questions().getFirst().position());
    }

    @Test
    void refusesABodyThatIsNotACatalogFile() {
        assertThrows(BadRequestResponse.class, () -> service.read(json("{\"something\": 1}")));
        assertThrows(BadRequestResponse.class, () -> service.read(json("[]")));
    }

    // -- Importing --

    @Test
    void createsTheCatalogWithItsQuestionsAndCategories() {
        var outcome = service.importInto(STATION_ID, service.read(json("""
                        {
                          "formatVersion": 1,
                          "catalog": {"name": "Grundwissen", "description": "Basis", "trainingEnabled": true,
                                      "metadata": {"source": "Fragenkatalog", "license": "CC BY-SA 4.0"}},
                          "categories": [{"key": "leitern", "name": "Leitern", "description": "Steigen", "position": 3}],
                          "questions": [
                            {"categoryKey": "leitern", "quizQuestionType": "TRUE_FALSE",
                             "title": "Die Schiebleiter ist höher als die Steckleiter",
                             "config": {"correctAnswer": true}},
                            {"quizQuestionType": "FREE_ANSWER", "title": "Wofür steht UVV?",
                             "config": {"answers": ["Unfallverhütungsvorschrift"], "lines": 2}}
                          ]
                        }""")));

        assertTrue(outcome.problems().isEmpty());
        assertNotNull(outcome.catalog());
        assertEquals("Fragenkatalog", outcome.catalog().metadata().source());
        assertEquals("CC BY-SA 4.0", outcome.catalog().metadata().license());

        var commands = captureCommands(2);
        assertEquals(QuizQuestionType.TRUE_FALSE, commands.getFirst().questionType());
        assertInstanceOf(QuestionConfig.TrueFalse.class, commands.getFirst().config());
        assertEquals(100, commands.getFirst().categoryId());
        assertNull(commands.get(1).categoryId());
        assertEquals(0, commands.getFirst().position());
        assertEquals(1, commands.get(1).position());
    }

    /**
     * Categories belong to the station, so an import has to file them under the station and not
     * under the catalog it happens to be creating.
     */
    @Test
    void filesCreatedCategoriesUnderTheStation() {
        service.importInto(STATION_ID, service.read(json("""
                        {"formatVersion": 1, "catalog": {"name": "K"},
                         "categories": [{"key": "k", "name": "Knoten", "description": "Seile", "position": 2}],
                         "questions": [{"categoryKey": "k", "quizQuestionType": "TRUE_FALSE",
                                        "title": "Frage", "config": {"correctAnswer": true}}]}""")));

        verify(catalogService).createCategory(STATION_ID, "Knoten", "Seile", 2);
    }

    @Test
    void reusesACategoryTheStationAlreadyHas() {
        when(catalogService.findCategories(STATION_ID))
                .thenReturn(List.of(new QuizCategory(42, STATION_ID, "Knoten", "Vorhanden", 1)));

        service.importInto(STATION_ID, service.read(json("""
                        {"formatVersion": 1, "catalog": {"name": "K"},
                         "categories": [{"key": "k", "name": "knoten", "description": "Neu", "position": 9}],
                         "questions": [{"categoryKey": "k", "quizQuestionType": "TRUE_FALSE",
                                        "title": "Frage", "config": {"correctAnswer": true}}]}""")));

        verify(catalogService, never()).createCategory(anyInt(), anyString(), anyString(), anyInt());
        assertEquals(42, captureCommands(1).getFirst().categoryId());
    }

    /**
     * A file exported by an older version listed the whole station's categories, most of which the
     * catalog never used. Only the ones a question refers to are worth creating.
     */
    @Test
    void leavesCategoriesNoQuestionRefersToAlone() {
        service.importInto(STATION_ID, service.read(json("""
                        {"formatVersion": 1, "catalog": {"name": "K"},
                         "categories": [{"key": "used", "name": "Benutzt", "description": "", "position": 0},
                                        {"key": "unused", "name": "Unbenutzt", "description": "", "position": 1}],
                         "questions": [{"categoryKey": "used", "quizQuestionType": "TRUE_FALSE",
                                        "title": "Frage", "config": {"correctAnswer": true}}]}""")));

        verify(catalogService).createCategory(STATION_ID, "Benutzt", "", 0);
        verify(catalogService, never()).createCategory(anyInt(), eq("Unbenutzt"), anyString(), anyInt());
    }

    // -- Appending --

    @Test
    void appendsBehindTheQuestionsTheCatalogAlreadyHas() {
        var target = catalog(5, STATION_ID, "Grundwissen", "Basis", CatalogMetadata.none());
        when(questionService.findQuestions(5))
                .thenReturn(List.of(
                        question(1, QuizQuestionType.TRUE_FALSE, "Schon da", 0),
                        question(2, QuizQuestionType.TRUE_FALSE, "Auch schon da", 4)));

        var outcome = service.appendTo(target, service.read(json("""
                        {"formatVersion": 1, "catalog": {"name": "Egal"},
                         "categories": [],
                         "questions": [
                           {"quizQuestionType": "TRUE_FALSE", "title": "Neu", "config": {"correctAnswer": true}},
                           {"quizQuestionType": "TRUE_FALSE", "title": "Noch neuer", "config": {"correctAnswer": false}}
                         ]}""")));

        assertTrue(outcome.problems().isEmpty());
        var commands = captureCommands(2);
        assertEquals(5, commands.getFirst().catalogId());
        assertEquals(5, commands.getFirst().position());
        assertEquals(6, commands.get(1).position());
        verify(catalogService, never()).createCatalog(anyInt(), anyString(), anyString(), anyBoolean(), any());
    }

    /** A file added to a catalog contributes questions; what the catalog is stays the catalog's. */
    @Test
    void appendingAcceptsAFileThatNamesNoCatalog() {
        var target = catalog(5, STATION_ID, "Grundwissen", "Basis", CatalogMetadata.none());
        when(questionService.findQuestions(5)).thenReturn(List.of());

        var outcome = service.appendTo(target, service.read(json("""
                        {"formatVersion": 1, "catalog": {"description": "ohne Namen"}, "categories": [],
                         "questions": [{"quizQuestionType": "TRUE_FALSE", "title": "Neu",
                                        "config": {"correctAnswer": true}}]}""")));

        assertTrue(outcome.problems().isEmpty());
        assertEquals(0, captureCommands(1).getFirst().position());
    }

    @Test
    void appendingStillRefusesAFaultyFileWholesale() {
        var target = catalog(5, STATION_ID, "Grundwissen", "Basis", CatalogMetadata.none());

        var outcome = service.appendTo(target, service.read(json("""
                        {"formatVersion": 1, "catalog": {"name": "K"}, "categories": [],
                         "questions": [{"quizQuestionType": "SORTIEREN", "title": "Unbekannt", "config": {}}]}""")));

        assertEquals("questions[0]", outcome.problems().getFirst().location());
        verify(questionService, never()).createQuestion(any());
    }

    // -- Refusing --

    @Test
    void reportsEveryProblemAtOnceAndCreatesNothing() {
        var outcome = service.importInto(STATION_ID, service.read(json("""
                        {"formatVersion": 1, "catalog": {"description": "Ohne Namen"},
                         "categories": [{"key": "a", "name": "Erste", "description": "", "position": 0}],
                         "questions": [
                           {"quizQuestionType": "TRUE_FALSE", "title": "", "config": {"correctAnswer": true}},
                           {"quizQuestionType": "SORTIEREN", "title": "Unbekannter Typ", "config": {}},
                           {"categoryKey": "fehlt", "quizQuestionType": "TRUE_FALSE",
                            "title": "Zeigt auf nichts", "config": {"correctAnswer": true}},
                           {"quizQuestionType": "ORDERING", "title": "Kaputte Antwort",
                            "config": {"items": "keine Liste"}}
                         ]}""")));

        assertNull(outcome.catalog());
        assertEquals(
                List.of("catalog.name", "questions[0]", "questions[1]", "questions[2]", "questions[3]"),
                outcome.problems().stream()
                        .map(QuizCatalogTransferService.TransferProblem::location)
                        .sorted()
                        .toList());
        verify(catalogService, never()).createCatalog(anyInt(), anyString(), anyString(), anyBoolean(), any());
        verify(questionService, never()).createQuestion(any());
    }

    @Test
    void refusesAFileWrittenForALaterVersion() {
        var transfer = new CatalogTransfer(
                CatalogTransfer.FORMAT_VERSION + 1,
                new CatalogTransfer.CatalogInfo("Zukunft", "", false, CatalogMetadata.none()),
                List.of(),
                List.of());

        var outcome = service.importInto(STATION_ID, transfer);

        assertNull(outcome.catalog());
        assertEquals("formatVersion", outcome.problems().getFirst().location());
    }

    @Test
    void refusesTwoCategoriesUnderOneKey() {
        var outcome = service.importInto(STATION_ID, service.read(json("""
                        {"formatVersion": 1, "catalog": {"name": "K"},
                         "categories": [{"key": "a", "name": "Erste", "description": "", "position": 0},
                                        {"key": "a", "name": "Zweite", "description": "", "position": 1}],
                         "questions": []}""")));

        assertNull(outcome.catalog());
        assertEquals("categories[1]", outcome.problems().getFirst().location());
    }

    // -- Exporting --

    @Test
    void exportsOnlyTheCategoriesItsOwnQuestionsUse() {
        var source = catalog(5, STATION_ID, "Grundwissen", "Basis", new CatalogMetadata("de", "Sheet", "Wache", null));
        when(catalogService.findCategories(STATION_ID))
                .thenReturn(List.of(
                        new QuizCategory(1, STATION_ID, "Wasserführende Armaturen", "Strahlrohre", 0),
                        new QuizCategory(2, STATION_ID, "Nie benutzt", "", 1)));
        when(questionService.findQuestions(5))
                .thenReturn(List.of(new QuizQuestion(
                        900,
                        5,
                        1,
                        QuizQuestionType.TRUE_FALSE,
                        "Ein C-Schlauch ist 15 Meter lang",
                        "",
                        null,
                        1,
                        true,
                        new QuestionConfig.TrueFalse(true),
                        0,
                        Instant.EPOCH,
                        Instant.EPOCH)));

        var transfer = service.export(source);

        assertEquals(CatalogTransfer.FORMAT_VERSION, transfer.formatVersion());
        assertEquals("de", transfer.catalog().metadata().language());
        assertEquals(1, transfer.categories().size());
        assertEquals(
                "wasserfuhrende-armaturen", transfer.categories().getFirst().key());
        assertEquals("wasserfuhrende-armaturen", transfer.questions().getFirst().categoryKey());
        assertEquals("TRUE_FALSE", transfer.questions().getFirst().quizQuestionType());
        assertTrue(
                transfer.questions().getFirst().config().path("correctAnswer").asBoolean());
    }

    @Test
    void roundTripsThroughTheFileItWrote() {
        var source = catalog(5, STATION_ID, "Grundwissen", "Basis", new CatalogMetadata("de", "Sheet", null, null));
        when(catalogService.findCategories(STATION_ID))
                .thenReturn(List.of(new QuizCategory(1, STATION_ID, "Leitern", "Steigen", 0)));
        when(questionService.findQuestions(5))
                .thenReturn(List.of(new QuizQuestion(
                        900,
                        5,
                        1,
                        QuizQuestionType.ENUMERATION,
                        "Nenne drei Knoten",
                        "",
                        null,
                        3,
                        true,
                        new QuestionConfig.Enumeration(List.of("Mastwurf", "Ankerstich", "Achterknoten"), 3, false, 1),
                        0,
                        Instant.EPOCH,
                        Instant.EPOCH)));

        JsonNode written = Json.MAPPER.valueToTree(service.export(source));
        var outcome = service.importInto(STATION_ID, service.read(written));

        assertTrue(outcome.problems().isEmpty());
        var command = captureCommands(1).getFirst();
        assertEquals(QuizQuestionType.ENUMERATION, command.questionType());
        var config = assertInstanceOf(QuestionConfig.Enumeration.class, command.config());
        assertEquals(List.of("Mastwurf", "Ankerstich", "Achterknoten"), config.answers());
        assertEquals(3, config.requiredCount());
    }
}
