/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.form.service;

import dev.chojo.ember.api.Refusal;
import dev.chojo.ember.feature.form.entity.FormAnswerValue;
import dev.chojo.ember.feature.form.entity.FormPage;
import dev.chojo.ember.feature.form.entity.FormQuestion;
import dev.chojo.ember.feature.form.entity.PageTarget;
import dev.chojo.ember.feature.form.entity.QuestionBranch;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Walks a form's pages with the answers a reader sent, the way the reader's browser walked them.
 *
 * <p>The browser is not trusted to have done it: from the first page on, each page leads where the
 * answer to its deciding question says, or where the page itself says otherwise, until a page sends
 * the form. Only the pages visited count. A required question on a page the path skipped is not
 * missing, and an answer to such a question is dropped: it comes from a reader who went down one
 * branch, went back and took the other, and was never meant to answer it.
 *
 * <p>Empty answers are dropped before anything is checked, since every fill screen sends an answer
 * of the right shape for every question, answered or not.
 *
 * <p>The same walk runs in the browser. Both are tested against the same example forms, kept as
 * shared fixtures, so the two cannot drift apart.
 */
public final class FormPathWalker {
    private FormPathWalker() {}

    /**
     * What a walk found.
     *
     * @param path     the keys of the pages visited, in order
     * @param answers  the answers to the questions on those pages, empty ones left out
     * @param problems what is wrong with the answers, one entry per question; empty where they can be taken
     */
    public record Walk(
            List<String> path, Map<Integer, FormAnswerValue> answers, List<FormAnswersRefused.Problem> problems) {}

    /**
     * Walks the pages with the given answers.
     *
     * @param pages     the form's pages, in their order
     * @param questions the form's questions, page by page
     * @param answers   the answers sent, by question id
     * @return the path, the answers kept and what is wrong with them
     */
    public static Walk walk(List<FormPage> pages, List<FormQuestion> questions, Map<Integer, FormAnswerValue> answers) {
        var given = nonEmpty(answers);
        var problems = new ArrayList<FormAnswersRefused.Problem>();
        var known = questions.stream().map(FormQuestion::id).collect(Collectors.toSet());
        for (var questionId : given.keySet()) {
            if (!known.contains(questionId)) {
                problems.add(FormAnswersRefused.Problem.of(questionId, null, Refusal.ANSWER_TO_QUESTION_NOT_ON_FORM));
            }
        }
        var path = path(pages, questions, given);
        var visited = new HashSet<>(path);
        var kept = new LinkedHashMap<Integer, FormAnswerValue>();
        for (var question : questions) {
            if (!visited.contains(question.pageKey())) continue;
            var value = given.get(question.id());
            if (value == null) {
                if (question.required()) {
                    problems.add(FormAnswersRefused.Problem.of(
                            question.id(), question.pageKey(), Refusal.QUESTION_NEEDS_AN_ANSWER));
                }
                continue;
            }
            if (!question.config().validate(value).isEmpty()) {
                problems.add(FormAnswersRefused.Problem.of(
                        question.id(), question.pageKey(), Refusal.ANSWER_DOES_NOT_FIT_QUESTION));
                continue;
            }
            kept.put(question.id(), value);
        }
        return new Walk(path, kept, problems);
    }

    /**
     * The keys of the pages the answers lead through, from the first page to the one the form is sent
     * from.
     *
     * @param pages     the form's pages, in their order
     * @param questions the form's questions
     * @param answers   the answers, empty ones already left out
     * @return the path
     */
    public static List<String> path(
            List<FormPage> pages, List<FormQuestion> questions, Map<Integer, FormAnswerValue> answers) {
        var path = new ArrayList<String>();
        int at = pages.isEmpty() ? -1 : 0;
        while (at >= 0 && at < pages.size()) {
            var page = pages.get(at);
            path.add(page.key());
            at = following(pages, at, targetOf(page, questions, answers));
        }
        return path;
    }

    /**
     * Where a page leads with the given answers: where its deciding question's answer says, and where
     * the page itself says otherwise.
     */
    private static PageTarget targetOf(
            FormPage page, List<FormQuestion> questions, Map<Integer, FormAnswerValue> answers) {
        for (FormQuestion question : questions) {
            QuestionBranch branch = question.branch();
            if (page.key().equals(question.pageKey()) && branch != null) {
                return pickedOption(answers.get(question.id()))
                        .map(branch::targetOf)
                        .orElseGet(() -> PageTarget.orNext(page.after()));
            }
        }
        return PageTarget.orNext(page.after());
    }

    private static Optional<String> pickedOption(FormAnswerValue value) {
        if (value instanceof FormAnswerValue.ChoiceAnswer(List<String> selected, var _)
                && selected != null
                && selected.size() == 1) {
            return Optional.of(selected.getFirst());
        }
        return Optional.empty();
    }

    /**
     * The position of the page a target leads to from the page at the given position, or -1 where it
     * ends the form. A target that does not lead further down ends the form too, so no walk can loop.
     */
    private static int following(List<FormPage> pages, int from, PageTarget target) {
        return switch (target.kind()) {
            case SUBMIT -> -1;
            case NEXT -> from + 1 < pages.size() ? from + 1 : -1;
            case PAGE -> {
                int to = -1;
                for (int i = 0; i < pages.size(); i++) {
                    if (pages.get(i).key().equals(target.page())) to = i;
                }
                yield to > from ? to : -1;
            }
        };
    }

    private static Map<Integer, FormAnswerValue> nonEmpty(Map<Integer, FormAnswerValue> answers) {
        var given = new LinkedHashMap<Integer, FormAnswerValue>();
        if (answers == null) return given;
        answers.forEach((id, value) -> {
            if (id != null && value != null && !value.isEmpty()) given.put(id, value);
        });
        return given;
    }
}
