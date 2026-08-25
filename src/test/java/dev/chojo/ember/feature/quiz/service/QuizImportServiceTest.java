/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.quiz.service;

import dev.chojo.ember.feature.quiz.entity.QuestionConfig;
import dev.chojo.ember.feature.quiz.entity.QuizQuestionType;
import dev.chojo.ember.feature.quiz.service.QuizImportService.CsvMappings;
import dev.chojo.ember.feature.quiz.service.QuizImportService.DraftQuestion;
import io.javalin.http.BadRequestResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuizImportServiceTest {

    private QuizImportService service;

    @BeforeEach
    void setUp() {
        service = new QuizImportService();
    }

    private static CsvMappings mappings(String typeColumn, QuizQuestionType defaultType) {
        return new CsvMappings(
                "Frage",
                "Antwort",
                "Kategorie",
                typeColumn,
                "Punkte",
                "Beschreibung",
                "Bild",
                "Falsch",
                "ProAntwort",
                "Anzahl",
                "Geordnet",
                ",",
                ";",
                defaultType);
    }

    private static QuestionConfig configOf(DraftQuestion draft) {
        return QuizQuestionType.valueOf(draft.question().quizQuestionType())
                .readConfig(draft.question().config().toString())
                .orElseThrow();
    }

    private QuestionConfig draftOne(QuizQuestionType type, String answer) {
        var draft = service.draft("Frage,Antwort\nTitel,%s\n".formatted(answer), mappings("Typ", type));
        return configOf(draft.questions().getFirst());
    }

    // -- Rows --

    @Test
    void draftsRowsAndSkipsBlankQuestions() {
        String csv = """
                Frage,Antwort,Kategorie,Typ,Punkte
                Was ist 1+1?,2;3,Mathe,MC,4
                ,ignored,Mathe,MC,1
                Zweite Frage,ja,Mathe,MC,
                """;

        var draft = service.draft(csv, mappings("Typ", null));

        assertEquals(2, draft.questions().size());
        var first = draft.questions().getFirst().question();
        assertEquals("Was ist 1+1?", first.title());
        assertEquals(4.0, first.points());
        assertTrue(first.autoPoints());
        assertEquals(0, first.position());
        assertEquals("Zweite Frage", draft.questions().get(1).question().title());
        assertEquals(1.0, draft.questions().get(1).question().points());
        assertEquals(
                1,
                draft.questions().get(1).question().position(),
                "a skipped row leaves no gap, because the draft is what gets created");
    }

    @Test
    void keepsTheAnswerCellSoTheWizardCanSplitItAgain() {
        var draft = service.draft("Frage,Antwort\nA,eins;zwei\n", mappings("Typ", null));

        assertEquals("eins;zwei", draft.questions().getFirst().rawAnswer());
        assertEquals(";", draft.questions().getFirst().answerSeparator());
    }

    @Test
    void collectsEachCategoryOnceAndKeysItByName() {
        String csv = """
                Frage,Antwort,Kategorie
                A,x,Wasserführende Armaturen
                B,x,WASSERFÜHRENDE ARMATUREN
                C,x,Brandlehre
                D,x,
                """;

        var draft = service.draft(csv, mappings("Typ", null));

        assertEquals(
                List.of("wasserfuhrende-armaturen", "brandlehre"),
                draft.categories().stream().map(c -> c.key()).toList());
        assertEquals("Wasserführende Armaturen", draft.categories().getFirst().name());
        assertEquals(
                "wasserfuhrende-armaturen",
                draft.questions().get(1).question().categoryKey(),
                "a name spelled differently down the sheet is still the one category");
        assertNull(draft.questions().get(3).question().categoryKey());
    }

    @Test
    void readsTheFurtherColumnsWhenTheSheetHasThem() {
        String csv = """
                Frage,Antwort,Beschreibung,Bild
                A,x,Ein Hinweis,https://example.invalid/a.png
                """;

        var question =
                service.draft(csv, mappings("Typ", null)).questions().getFirst().question();

        assertEquals("Ein Hinweis", question.description());
        assertEquals("https://example.invalid/a.png", question.imageUrl());
    }

    @Test
    void leavesTheImageUnsetWhenNoColumnCarriesOne() {
        var question = service.draft("Frage,Antwort\nA,x\n", mappings("Typ", null))
                .questions()
                .getFirst()
                .question();

        assertNull(question.imageUrl());
        assertEquals("", question.description());
    }

    // -- Types --

    @Test
    void fallsBackToTheDefaultTypeWhenNoTypeColumnIsMapped() {
        var draft = service.draft("Frage,Antwort\nA,wahr\n", mappings("Typ", QuizQuestionType.TRUE_FALSE));

        assertEquals("TRUE_FALSE", draft.questions().getFirst().question().quizQuestionType());
        assertInstanceOf(
                QuestionConfig.TrueFalse.class, configOf(draft.questions().getFirst()));
    }

    @Test
    void fallsBackToMultipleChoiceWithoutTypeColumnOrDefaultType() {
        var draft = service.draft("Frage,Antwort\nA,eins;zwei\n", mappings("Typ", null));

        assertEquals("MULTIPLE_CHOICE", draft.questions().getFirst().question().quizQuestionType());
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

        var types = service.draft(csv, mappings("Typ", null)).questions().stream()
                .map(draft -> draft.question().quizQuestionType())
                .toList();

        assertEquals(
                List.of(
                        "MULTIPLE_CHOICE",
                        "MULTIPLE_CHOICE",
                        "TRUE_FALSE",
                        "TRUE_FALSE",
                        "TRUE_FALSE",
                        "FREE_ANSWER",
                        "FREE_ANSWER",
                        "FREE_ANSWER",
                        "FILL_IN_THE_BLANK",
                        "FILL_IN_THE_BLANK",
                        "FILL_IN_THE_BLANK",
                        "FILL_IN_THE_BLANK",
                        "CONNECT",
                        "CONNECT",
                        "ORDERING",
                        "ORDERING",
                        "IMAGE_TEXT",
                        "ENUMERATION",
                        "ENUMERATION",
                        "ENUMERATION"),
                types);
    }

    // -- Refusals --

    @Test
    void rejectsASheetWithoutTheQuestionColumn() {
        assertThrows(BadRequestResponse.class, () -> service.draft("Titel,Antwort\nA,x\n", mappings("Typ", null)));
    }

    @Test
    void rejectsAnUnknownQuestionType() {
        assertThrows(
                BadRequestResponse.class,
                () -> service.draft("Frage,Antwort,Typ\nA,x,Kreuzwortraetsel\n", mappings("Typ", null)));
    }

    @Test
    void rejectsAnUnparsableSheet() {
        assertThrows(BadRequestResponse.class, () -> service.draft("\"Frage,Antwort\nA,x\n", mappings("Typ", null)));
    }

    @Test
    void keepsTheDefaultPointsWhenThePointsCellIsNotANumber() {
        var draft = service.draft("Frage,Antwort,Punkte\nA,x,drei\n", mappings("Typ", null));

        assertEquals(1.0, draft.questions().getFirst().question().points());
    }

    // -- Configs --

    @Test
    void marksTheFirstAnswerCorrectWhenNoColumnHoldsTheWrongOnes() {
        var config = assertInstanceOf(
                QuestionConfig.MultipleChoice.class, draftOne(QuizQuestionType.MULTIPLE_CHOICE, "A; B ;C"));

        assertEquals(3, config.options().size());
        assertEquals("A", config.options().getFirst().text());
        assertTrue(config.options().getFirst().correct());
        assertFalse(config.options().get(1).correct());
        assertEquals(0.5, config.pointsPerCorrect());
    }

    /**
     * A sheet that keeps its wrong answers apart means every answer in the answer cell, so the
     * reading of that cell changes with the second column rather than staying positional.
     */
    @Test
    void takesEveryAnswerAsCorrectWhenAColumnHoldsTheWrongOnes() {
        String csv = """
                Frage,Antwort,Falsch,Typ
                Welche Klassen gibt es?,A;B,X;Y,MC
                """;

        var config = assertInstanceOf(
                QuestionConfig.MultipleChoice.class,
                configOf(service.draft(csv, mappings("Typ", null)).questions().getFirst()));

        assertEquals(
                List.of("A", "B", "X", "Y"),
                config.options().stream().map(o -> o.text()).toList());
        assertEquals(
                List.of(true, true, false, false),
                config.options().stream().map(o -> o.correct()).toList());
    }

    /**
     * A blank with nothing to pick from is the one shape these catalogs should not produce, so a
     * distractor column turns a fill-in into a list the member chooses from.
     */
    @Test
    void offersFillInTheBlankAsAListWhenDistractorsAreGiven() {
        String csv = """
                Frage,Antwort,Falsch,Typ
                Ein C-Schlauch ist __ lang,15 Meter,20 Meter;30 Meter,fill_blank
                """;

        var config = assertInstanceOf(
                QuestionConfig.FillInTheBlank.class,
                configOf(service.draft(csv, mappings("Typ", null)).questions().getFirst()));

        assertEquals(List.of("15 Meter"), config.answers());
        assertEquals(List.of("20 Meter", "30 Meter"), config.distractors());
        assertTrue(config.useDropdown());
    }

    @Test
    void leavesFillInTheBlankAsAnOpenBoxWithoutDistractors() {
        var config = assertInstanceOf(
                QuestionConfig.FillInTheBlank.class, draftOne(QuizQuestionType.FILL_IN_THE_BLANK, "Paris;Berlin"));

        assertEquals("", config.text());
        assertEquals(List.of("Paris", "Berlin"), config.answers());
        assertEquals(List.of(), config.distractors());
        assertFalse(config.useDropdown());
    }

    @Test
    void buildsTrueFalseFromTheAffirmativeSpellings() {
        assertTrue(((QuestionConfig.TrueFalse) draftOne(QuizQuestionType.TRUE_FALSE, "true")).correctAnswer());
        assertTrue(((QuestionConfig.TrueFalse) draftOne(QuizQuestionType.TRUE_FALSE, "1")).correctAnswer());
        assertTrue(((QuestionConfig.TrueFalse) draftOne(QuizQuestionType.TRUE_FALSE, "Wahr")).correctAnswer());
        assertTrue(((QuestionConfig.TrueFalse) draftOne(QuizQuestionType.TRUE_FALSE, "ja")).correctAnswer());
        assertFalse(((QuestionConfig.TrueFalse) draftOne(QuizQuestionType.TRUE_FALSE, "nein")).correctAnswer());
    }

    @Test
    void buildsFreeAnswerWithThreeLines() {
        var config = assertInstanceOf(
                QuestionConfig.FreeAnswer.class, draftOne(QuizQuestionType.FREE_ANSWER, "Paris;Berlin"));

        assertEquals(List.of("Paris", "Berlin"), config.answers());
        assertEquals(3, config.lines());
    }

    @Test
    void buildsConnectPairsFromEqualsSeparatedCells() {
        var config = assertInstanceOf(QuestionConfig.Connect.class, draftOne(QuizQuestionType.CONNECT, "A=1;B"));

        assertEquals(2, config.pairs().size());
        assertEquals("A", config.pairs().getFirst().left());
        assertEquals("1", config.pairs().getFirst().right());
        assertEquals("B", config.pairs().get(1).left());
        assertEquals("", config.pairs().get(1).right());
    }

    @Test
    void buildsOrderingFromTheAnswerList() {
        var config = assertInstanceOf(QuestionConfig.Ordering.class, draftOne(QuizQuestionType.ORDERING, "A;B;C"));

        assertEquals(List.of("A", "B", "C"), config.items());
    }

    @Test
    void keepsTheAnswerCellOfAnImageQuestion() {
        var config =
                assertInstanceOf(QuestionConfig.ImageText.class, draftOne(QuizQuestionType.IMAGE_TEXT, "Verteiler"));

        assertNull(config.imageUrl());
        assertEquals("Verteiler", config.answer());
    }

    @Test
    void buildsEnumerationCappedAtThreeRequiredAnswers() {
        var many =
                assertInstanceOf(QuestionConfig.Enumeration.class, draftOne(QuizQuestionType.ENUMERATION, "a;b;c;d"));
        assertEquals(List.of("a", "b", "c", "d"), many.answers());
        assertEquals(3, many.requiredCount());
        assertFalse(many.orderedRequired());

        var few = assertInstanceOf(QuestionConfig.Enumeration.class, draftOne(QuizQuestionType.ENUMERATION, "a;b"));
        assertEquals(2, few.requiredCount());
    }

    @Test
    void takesTheEnumerationCountAndOrderFromTheSheetWhenMapped() {
        String csv = """
                Frage,Antwort,Anzahl,Geordnet,Typ
                Nenne die Rettungskette,a;b;c;d;e,5,ja,enumeration
                """;

        var config = assertInstanceOf(
                QuestionConfig.Enumeration.class,
                configOf(service.draft(csv, mappings("Typ", null)).questions().getFirst()));

        assertEquals(5, config.requiredCount());
        assertTrue(config.orderedRequired());
    }

    @Test
    void takesThePerAnswerPointsFromTheSheetWhenMapped() {
        String csv = """
                Frage,Antwort,ProAntwort,Typ
                A,a;b;c,2.5,ordering
                """;

        var config = assertInstanceOf(
                QuestionConfig.Ordering.class,
                configOf(service.draft(csv, mappings("Typ", null)).questions().getFirst()));

        assertEquals(2.5, config.pointsPerCorrect());
    }

    // -- Separators --

    @Test
    void honoursCustomSeparators() {
        var custom = new CsvMappings(
                "Frage",
                "Antwort",
                "Kategorie",
                "Typ",
                "Punkte",
                "",
                "",
                "",
                "",
                "",
                "",
                ";",
                "|",
                QuizQuestionType.ORDERING);

        var draft = service.draft("Frage;Antwort\nA;eins|zwei\n", custom);

        var config = assertInstanceOf(
                QuestionConfig.Ordering.class, configOf(draft.questions().getFirst()));
        assertEquals(List.of("eins", "zwei"), config.items());
    }

    @Test
    void fallsBackToTheDefaultSeparatorsWhenUnset() {
        var unset = new CsvMappings(
                "Frage",
                "Antwort",
                "Kategorie",
                "Typ",
                "Punkte",
                "",
                "",
                "",
                "",
                "",
                "",
                null,
                null,
                QuizQuestionType.ORDERING);

        var draft = service.draft("Frage,Antwort\nA,eins;zwei\n", unset);

        var config = assertInstanceOf(
                QuestionConfig.Ordering.class, configOf(draft.questions().getFirst()));
        assertEquals(List.of("eins", "zwei"), config.items());
    }

    @Test
    void readsADecimalWrittenWithAComma() {
        var semicolonSheet = new CsvMappings(
                "Frage", "Antwort", "Kategorie", "Typ", "Punkte", "", "", "", "", "", "", ";", ";", null);

        var draft = service.draft("Frage;Antwort;Punkte\nA;x;2,5\n", semicolonSheet);

        assertEquals(2.5, draft.questions().getFirst().question().points());
    }
}
