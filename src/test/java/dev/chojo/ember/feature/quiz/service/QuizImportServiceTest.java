/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.quiz.service;

import dev.chojo.ember.feature.quiz.entity.CreateQuestionCommand;
import dev.chojo.ember.feature.quiz.entity.QuestionConfig;
import dev.chojo.ember.feature.quiz.entity.QuizCatalog;
import dev.chojo.ember.feature.quiz.entity.QuizCategory;
import dev.chojo.ember.feature.quiz.entity.QuizQuestionType;
import io.javalin.http.BadRequestResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class QuizImportServiceTest {

    private static final QuizCatalog CATALOG =
            new QuizCatalog(42, 7, "Catalog", "", false, false, Instant.EPOCH, Instant.EPOCH);

    private QuizCatalogService catalogService;
    private QuizQuestionService questionService;
    private QuizImportService service;
    private int nextCategoryId;

    @BeforeEach
    void setUp() {
        catalogService = mock(QuizCatalogService.class);
        questionService = mock(QuizQuestionService.class);
        service = new QuizImportService(catalogService, questionService);
        nextCategoryId = 100;
        when(catalogService.findCategories(anyInt())).thenReturn(List.of());
        when(catalogService.createCategory(anyInt(), anyString(), anyString(), anyInt()))
                .thenAnswer(invocation -> new QuizCategory(
                        nextCategoryId++, invocation.getArgument(0), invocation.getArgument(1), "", 0));
    }

    private QuizImportService.CsvMappings mappings(String typeColumn, QuizQuestionType defaultType) {
        return new QuizImportService.CsvMappings(
                "Frage", "Antwort", "Kategorie", typeColumn, "Punkte", ",", ";", defaultType);
    }

    private List<CreateQuestionCommand> captureCommands(int expected) {
        var captor = ArgumentCaptor.forClass(CreateQuestionCommand.class);
        verify(questionService, times(expected)).createQuestion(captor.capture());
        return captor.getAllValues();
    }

    @Test
    void importsRowsAndSkipsBlankQuestions() {
        String csv = """
                Frage,Antwort,Kategorie,Typ,Punkte
                Was ist 1+1?,2;3,Mathe,MC,4
                ,ignored,Mathe,MC,1
                Zweite Frage,ja,Mathe,MC,
                """;

        var result = service.importCsv(CATALOG, csv, mappings("Typ", null));

        assertEquals(2, result.imported());
        var commands = captureCommands(2);
        assertEquals("Was ist 1+1?", commands.get(0).title());
        assertEquals(42, commands.get(0).catalogId());
        assertEquals(4.0, commands.get(0).points());
        assertTrue(commands.get(0).autoPoints());
        assertEquals(0, commands.get(0).position());
        assertEquals("Zweite Frage", commands.get(1).title());
        assertEquals(1.0, commands.get(1).points());
        assertEquals(2, commands.get(1).position(), "positions follow the sheet, not the import counter");
    }

    @Test
    void reusesExistingCategoriesAndCreatesMissingOnesOnce() {
        when(catalogService.findCategories(7)).thenReturn(List.of(new QuizCategory(5, 7, "Mathe", "", 0)));
        String csv = """
                Frage,Antwort,Kategorie,Typ,Punkte
                A,x,Mathe,MC,1
                B,x,MATHE,MC,1
                C,x,Physik,MC,1
                D,x,Physik,MC,1
                E,x,,MC,1
                """;

        service.importCsv(CATALOG, csv, mappings("Typ", null));

        var commands = captureCommands(5);
        assertEquals(5, commands.get(0).categoryId().intValue());
        assertEquals(5, commands.get(1).categoryId().intValue());
        assertEquals(commands.get(2).categoryId(), commands.get(3).categoryId());
        assertNull(commands.get(4).categoryId());
        verify(catalogService, times(1)).createCategory(eq(7), eq("Physik"), eq(""), eq(1));
    }

    @Test
    void fallsBackToTheDefaultTypeWhenNoTypeColumnIsMapped() {
        String csv = """
                Frage,Antwort
                A,wahr
                """;

        service.importCsv(CATALOG, csv, mappings("Typ", QuizQuestionType.TRUE_FALSE));

        var command = captureCommands(1).getFirst();
        assertEquals(QuizQuestionType.TRUE_FALSE, command.questionType());
        assertInstanceOf(QuestionConfig.TrueFalse.class, command.config());
    }

    @Test
    void fallsBackToMultipleChoiceWithoutTypeColumnOrDefaultType() {
        String csv = """
                Frage,Antwort
                A,eins;zwei
                """;

        service.importCsv(CATALOG, csv, mappings("Typ", null));

        assertEquals(
                QuizQuestionType.MULTIPLE_CHOICE, captureCommands(1).getFirst().questionType());
    }

    @Test
    void keepsTheDefaultPointsWhenThePointsCellIsNotANumber() {
        String csv = """
                Frage,Antwort,Punkte
                A,x,drei
                """;

        service.importCsv(CATALOG, csv, mappings("Typ", null));

        assertEquals(1.0, captureCommands(1).getFirst().points());
    }

    @Test
    void rejectsASheetWithoutTheQuestionColumn() {
        String csv = """
                Titel,Antwort
                A,x
                """;

        assertThrows(BadRequestResponse.class, () -> service.importCsv(CATALOG, csv, mappings("Typ", null)));
        verify(questionService, never()).createQuestion(any());
    }

    @Test
    void acceptsEveryEnglishAndGermanTypeSpelling() {
        String csv = """
                Frage,Antwort,Typ
                a,x,MC
                b,x,Multiple Choice
                c,x,TF
                d,x,true_false
                e,x,wahr-falsch
                f,x,FREE
                g,x,free_answer
                h,x,Freitext
                i,x,fill_blank
                j,x,FILL_IN_THE_BLANK
                k,x,Lückentext
                l,x,LUECKENTEXT
                m,x,connect
                n,x,Zuordnung
                o,x,ordering
                p,x,Reihenfolge
                q,x,image_text
                r,x,enumeration
                s,x,Aufzählung
                t,x,AUFZAEHLUNG
                """;

        service.importCsv(CATALOG, csv, mappings("Typ", null));

        var types = captureCommands(20).stream()
                .map(CreateQuestionCommand::questionType)
                .toList();
        assertEquals(
                List.of(
                        QuizQuestionType.MULTIPLE_CHOICE,
                        QuizQuestionType.MULTIPLE_CHOICE,
                        QuizQuestionType.TRUE_FALSE,
                        QuizQuestionType.TRUE_FALSE,
                        QuizQuestionType.TRUE_FALSE,
                        QuizQuestionType.FREE_ANSWER,
                        QuizQuestionType.FREE_ANSWER,
                        QuizQuestionType.FREE_ANSWER,
                        QuizQuestionType.FILL_IN_THE_BLANK,
                        QuizQuestionType.FILL_IN_THE_BLANK,
                        QuizQuestionType.FILL_IN_THE_BLANK,
                        QuizQuestionType.FILL_IN_THE_BLANK,
                        QuizQuestionType.CONNECT,
                        QuizQuestionType.CONNECT,
                        QuizQuestionType.ORDERING,
                        QuizQuestionType.ORDERING,
                        QuizQuestionType.IMAGE_TEXT,
                        QuizQuestionType.ENUMERATION,
                        QuizQuestionType.ENUMERATION,
                        QuizQuestionType.ENUMERATION),
                types);
    }

    @Test
    void rejectsAnUnknownQuestionType() {
        String csv = """
                Frage,Antwort,Typ
                A,x,Kreuzwortraetsel
                """;

        assertThrows(BadRequestResponse.class, () -> service.importCsv(CATALOG, csv, mappings("Typ", null)));
    }

    @Test
    void rejectsAnUnparsableSheet() {
        String csv = "\"Frage,Antwort\nA,x\n";

        assertThrows(BadRequestResponse.class, () -> service.importCsv(CATALOG, csv, mappings("Typ", null)));
    }

    @Test
    void buildsMultipleChoiceWithTheFirstAnswerMarkedCorrect() {
        var config = assertInstanceOf(
                QuestionConfig.MultipleChoice.class, importOne(QuizQuestionType.MULTIPLE_CHOICE, "A; B ;C"));

        assertEquals(3, config.options().size());
        assertEquals("A", config.options().getFirst().text());
        assertTrue(config.options().getFirst().correct());
        assertFalse(config.options().get(1).correct());
        assertEquals(0.5, config.pointsPerCorrect());
    }

    @Test
    void buildsTrueFalseFromTheAffirmativeSpellings() {
        assertTrue(((QuestionConfig.TrueFalse) importOne(QuizQuestionType.TRUE_FALSE, "true")).correctAnswer());
        assertTrue(((QuestionConfig.TrueFalse) importOne(QuizQuestionType.TRUE_FALSE, "1")).correctAnswer());
        assertTrue(((QuestionConfig.TrueFalse) importOne(QuizQuestionType.TRUE_FALSE, "Wahr")).correctAnswer());
        assertFalse(((QuestionConfig.TrueFalse) importOne(QuizQuestionType.TRUE_FALSE, "nein")).correctAnswer());
    }

    @Test
    void buildsFreeAnswerWithThreeLines() {
        var config = assertInstanceOf(
                QuestionConfig.FreeAnswer.class, importOne(QuizQuestionType.FREE_ANSWER, "Paris;Berlin"));

        assertEquals(List.of("Paris", "Berlin"), config.answers());
        assertEquals(3, config.lines());
    }

    @Test
    void buildsFillInTheBlankFromTheAnswerList() {
        var config = assertInstanceOf(
                QuestionConfig.FillInTheBlank.class, importOne(QuizQuestionType.FILL_IN_THE_BLANK, "Paris;Berlin"));

        assertEquals("", config.text());
        assertEquals(List.of("Paris", "Berlin"), config.answers());
        assertEquals(List.of(), config.distractors());
        assertFalse(config.useDropdown());
    }

    @Test
    void buildsConnectPairsFromEqualsSeparatedCells() {
        var config = assertInstanceOf(QuestionConfig.Connect.class, importOne(QuizQuestionType.CONNECT, "A=1;B"));

        assertEquals(2, config.pairs().size());
        assertEquals("A", config.pairs().getFirst().left());
        assertEquals("1", config.pairs().getFirst().right());
        assertEquals("B", config.pairs().get(1).left());
        assertEquals("", config.pairs().get(1).right());
    }

    @Test
    void buildsOrderingFromTheAnswerList() {
        var config = assertInstanceOf(QuestionConfig.Ordering.class, importOne(QuizQuestionType.ORDERING, "A;B;C"));

        assertEquals(List.of("A", "B", "C"), config.items());
    }

    @Test
    void buildsImageTextWithoutAnyAnswer() {
        var config = assertInstanceOf(QuestionConfig.ImageText.class, importOne(QuizQuestionType.IMAGE_TEXT, "x"));

        assertNull(config.imageUrl());
        assertNull(config.answer());
    }

    @Test
    void buildsEnumerationCappedAtThreeRequiredAnswers() {
        var many =
                assertInstanceOf(QuestionConfig.Enumeration.class, importOne(QuizQuestionType.ENUMERATION, "a;b;c;d"));
        assertEquals(List.of("a", "b", "c", "d"), many.answers());
        assertEquals(3, many.requiredCount());
        assertFalse(many.orderedRequired());

        var few = assertInstanceOf(QuestionConfig.Enumeration.class, importOne(QuizQuestionType.ENUMERATION, "a;b"));
        assertEquals(2, few.requiredCount());
    }

    @Test
    void honoursCustomSeparators() {
        var mappings = new QuizImportService.CsvMappings(
                "Frage", "Antwort", "Kategorie", "Typ", "Punkte", ";", "|", QuizQuestionType.ORDERING);

        service.importCsv(CATALOG, "Frage;Antwort\nA;eins|zwei\n", mappings);

        var config = assertInstanceOf(
                QuestionConfig.Ordering.class, captureCommands(1).getFirst().config());
        assertEquals(List.of("eins", "zwei"), config.items());
    }

    @Test
    void fallsBackToTheDefaultSeparatorsWhenUnset() {
        var mappings = new QuizImportService.CsvMappings(
                "Frage", "Antwort", "Kategorie", "Typ", "Punkte", null, null, QuizQuestionType.ORDERING);

        service.importCsv(CATALOG, "Frage,Antwort\nA,eins;zwei\n", mappings);

        var config = assertInstanceOf(
                QuestionConfig.Ordering.class, captureCommands(1).getFirst().config());
        assertEquals(List.of("eins", "zwei"), config.items());
    }

    private QuestionConfig importOne(QuizQuestionType type, String answer) {
        catalogService = mock(QuizCatalogService.class);
        questionService = mock(QuizQuestionService.class);
        service = new QuizImportService(catalogService, questionService);
        when(catalogService.findCategories(anyInt())).thenReturn(List.of());
        service.importCsv(CATALOG, "Frage,Antwort\nTitel,%s\n".formatted(answer), mappings("Typ", type));
        return captureCommands(1).getFirst().config();
    }
}
