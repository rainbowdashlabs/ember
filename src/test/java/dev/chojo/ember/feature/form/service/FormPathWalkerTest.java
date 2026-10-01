/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.form.service;

import dev.chojo.ember.api.refusal.FormRefusal;
import dev.chojo.ember.feature.form.entity.FormAnswerValue;
import dev.chojo.ember.feature.form.entity.FormPage;
import dev.chojo.ember.feature.form.entity.FormQuestion;
import dev.chojo.ember.feature.form.entity.FormQuestionConfig;
import dev.chojo.ember.feature.form.entity.FormQuestionType;
import dev.chojo.ember.feature.form.entity.PageTarget;
import dev.chojo.ember.feature.form.entity.QuestionBranch;
import dev.chojo.ember.util.Json;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import tools.jackson.databind.JsonNode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The server's walk through a form's pages.
 *
 * <p>The example forms are read from the fixtures the browser's walk is tested against, so the two
 * walks come to the same path or one of the two tests fails.
 */
class FormPathWalkerTest {
    private static final Path FIXTURES = Path.of("frontend/src/util/formPath.fixtures.json");

    @TestFactory
    Stream<DynamicTest> walksTheSameExamplesAsTheBrowser() throws IOException {
        var fixtures = Json.MAPPER.readTree(Files.readString(FIXTURES));
        var tests = new ArrayList<DynamicTest>();
        for (var fixture : fixtures) {
            tests.add(DynamicTest.dynamicTest(fixture.get("name").asString(), () -> {
                var questions = questions(fixture.get("questions"));
                var path = FormPathWalker.path(pages(fixture.get("pages")), questions, answers(fixture, questions));
                assertEquals(strings(fixture.get("path")), path);
            }));
        }
        return tests.stream();
    }

    @Test
    void aRequiredQuestionOnAVisitedPageNeedsAnAnswer() {
        var pages = List.of(page("p0", PageTarget.NEXT));
        var question = text(1, "p0", true);

        var walk = FormPathWalker.walk(pages, List.of(question), Map.of(1, new FormAnswerValue.TextAnswer("  ")));

        assertEquals(
                List.of(FormAnswersRefused.AnswerProblem.of(1, "p0", FormRefusal.QUESTION_NEEDS_AN_ANSWER)),
                walk.problems());
    }

    @Test
    void aRequiredQuestionOnASkippedPageIsNotMissingAndItsAnswerIsDropped() {
        var pages =
                List.of(page("p0", PageTarget.page("p2")), page("p1", PageTarget.NEXT), page("p2", PageTarget.NEXT));
        var skipped = text(1, "p1", true);
        var asked = text(2, "p2", false);

        var walk = FormPathWalker.walk(
                pages,
                List.of(skipped, asked),
                Map.of(1, new FormAnswerValue.TextAnswer("stale"), 2, new FormAnswerValue.TextAnswer("kept")));

        assertEquals(List.of("p0", "p2"), walk.path());
        assertEquals(List.of(), walk.problems());
        assertEquals(Map.of(2, new FormAnswerValue.TextAnswer("kept")), walk.answers());
    }

    @Test
    void anOptionalChoiceOrRatingLeftEmptyIsNoAnswerRatherThanAWrongOne() {
        var pages = List.of(page("p0", PageTarget.NEXT));
        var choice = new FormQuestion(
                1,
                1,
                0,
                "p0",
                FormQuestionType.CHOICE,
                "Wahl",
                "",
                false,
                false,
                new FormQuestionConfig.Choice(FormQuestionConfig.Option.numbered("a"), false, false, false, null, null),
                null);
        var rating = new FormQuestion(
                2,
                1,
                1,
                "p0",
                FormQuestionType.RATING,
                "Sterne",
                "",
                false,
                false,
                new FormQuestionConfig.Rating(5, FormQuestionConfig.Rating.RatingIcon.STAR),
                null);

        var walk = FormPathWalker.walk(
                pages,
                List.of(choice, rating),
                Map.of(1, new FormAnswerValue.ChoiceAnswer(List.of(), ""), 2, new FormAnswerValue.RatingAnswer(0)));

        assertEquals(List.of(), walk.problems());
        assertEquals(Map.of(), walk.answers());
    }

    @Test
    void anAnswerThatDoesNotFitAndAnAnswerToNoQuestionAreEachNamed() {
        var pages = List.of(page("p0", PageTarget.NEXT));
        var rating = new FormQuestion(
                1,
                1,
                0,
                "p0",
                FormQuestionType.RATING,
                "Sterne",
                "",
                false,
                false,
                new FormQuestionConfig.Rating(5, null),
                null);

        var walk = FormPathWalker.walk(
                pages,
                List.of(rating),
                Map.of(1, new FormAnswerValue.RatingAnswer(9), 42, new FormAnswerValue.TextAnswer("?")));

        assertEquals(
                List.of(
                        FormAnswersRefused.AnswerProblem.of(42, null, FormRefusal.ANSWER_TO_QUESTION_NOT_ON_FORM),
                        FormAnswersRefused.AnswerProblem.of(1, "p0", FormRefusal.ANSWER_DOES_NOT_FIT_QUESTION)),
                walk.problems());
    }

    @Test
    void aChoiceOfOnlyItsOwnWordsIsAnAnswer() {
        var config =
                new FormQuestionConfig.Choice(FormQuestionConfig.Option.numbered("a"), false, false, true, null, null);

        assertEquals(List.of(), config.validate(new FormAnswerValue.ChoiceAnswer(List.of(), "selbst")));
    }

    private static List<FormPage> pages(JsonNode node) {
        var pages = new ArrayList<FormPage>();
        for (var page : node) {
            pages.add(page(page.get("key").asString(), target(page.get("after"))));
        }
        return pages;
    }

    private static List<FormQuestion> questions(JsonNode node) {
        var questions = new ArrayList<FormQuestion>();
        for (var question : node) {
            var type = FormQuestionType.valueOf(question.get("type").asString());
            QuestionBranch branch = null;
            if (question.hasNonNull("branch")) {
                var targets = new LinkedHashMap<String, PageTarget>();
                question.get("branch")
                        .properties()
                        .forEach(entry -> targets.put(entry.getKey(), target(entry.getValue())));
                branch = new QuestionBranch(targets);
            }
            questions.add(new FormQuestion(
                    question.get("id").asInt(),
                    1,
                    questions.size(),
                    question.get("pageKey").asString(),
                    type,
                    "Frage",
                    "",
                    false,
                    false,
                    FormQuestionConfig.parse(type, question.get("config").toString()),
                    branch));
        }
        return questions;
    }

    private static Map<Integer, FormAnswerValue> answers(JsonNode fixture, List<FormQuestion> questions) {
        var answers = new LinkedHashMap<Integer, FormAnswerValue>();
        for (var question : questions) {
            var answer = fixture.get("answers").get(String.valueOf(question.id()));
            if (answer == null) continue;
            var value = FormAnswerValue.parse(question.formQuestionType(), answer.toString());
            if (value != null && !value.isEmpty()) answers.put(question.id(), value);
        }
        return answers;
    }

    private static PageTarget target(JsonNode node) {
        var kind = PageTarget.TargetKind.valueOf(node.get("kind").asString());
        return new PageTarget(kind, node.hasNonNull("page") ? node.get("page").asString() : null);
    }

    private static List<String> strings(JsonNode node) {
        var strings = new ArrayList<String>();
        for (var value : node) strings.add(value.asString());
        return strings;
    }

    private static FormPage page(String key, PageTarget after) {
        return new FormPage(0, 1, key, 0, "", "", after);
    }

    private static FormQuestion text(int id, String pageKey, boolean required) {
        return new FormQuestion(
                id,
                1,
                id,
                pageKey,
                FormQuestionType.TEXT,
                "Text",
                "",
                required,
                false,
                new FormQuestionConfig.Text(false),
                null);
    }
}
