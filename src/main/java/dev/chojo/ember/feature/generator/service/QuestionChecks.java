/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.service;

import dev.chojo.ember.api.refusal.DocumentRefusal;
import dev.chojo.ember.api.refusal.RefusalDetail;
import dev.chojo.ember.feature.generator.entity.PlaceholderKey;
import dev.chojo.ember.feature.generator.entity.PlaceholderTokens;
import dev.chojo.ember.feature.generator.entity.TemplateQuestion;
import dev.chojo.ember.feature.generator.entity.TemplateQuestionKind;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/**
 * Checks the questions a template asks, each refusal naming the question it is about by its label, or by
 * its position where it has none, so the editor can say which one is wrong.
 *
 * <p>A suggestion may name any value the template could name itself, but no answer: a suggestion is
 * filled in before anybody answers.
 *
 * <p>A question keeps only the limits of its own kind ({@link AnswerLimits#checked}).
 */
final class QuestionChecks {
    /** The most questions one template asks. */
    static final int MAX_QUESTIONS = 100;

    /** The longest label of a question. */
    static final int MAX_LABEL = 120;

    /** The longest suggestion of a question. */
    static final int MAX_SUGGESTION = 600;

    private QuestionChecks() {}

    /**
     * @param raw the questions as the editor sent them, or null for none
     * @return the questions, stripped, in the order they were sent
     */
    static List<TemplateQuestion> checked(@Nullable List<TemplateQuestion> raw) {
        if (raw == null || raw.isEmpty()) return List.of();
        if (raw.size() > MAX_QUESTIONS) throw DocumentRefusal.DOCUMENT_TEMPLATE_QUESTIONS_TOO_MANY.raise();
        var keys = new HashSet<String>();
        var out = new ArrayList<TemplateQuestion>();
        for (int index = 0; index < raw.size(); index++) {
            var question = Objects.requireNonNull(raw.get(index), "a question");
            String label = strip(question.label());
            String named = label.isEmpty() ? "#" + (index + 1) : label;
            if (label.isEmpty()) {
                throw DocumentRefusal.DOCUMENT_TEMPLATE_QUESTION_LABEL_MISSING.raise(RefusalDetail.text(named));
            }
            if (label.length() > MAX_LABEL) {
                throw DocumentRefusal.DOCUMENT_TEMPLATE_TEXT_TOO_LONG.raise(RefusalDetail.text(named));
            }
            String key = strip(question.key());
            if (!TemplateQuestion.KEY.matcher(key).matches()) {
                throw DocumentRefusal.DOCUMENT_TEMPLATE_QUESTION_KEY_INVALID.raise(RefusalDetail.text(named));
            }
            if (!keys.add(key)) {
                throw DocumentRefusal.DOCUMENT_TEMPLATE_QUESTION_KEY_TWICE.raise(RefusalDetail.text(named));
            }
            String suggestion = strip(question.suggestion());
            if (suggestion.length() > MAX_SUGGESTION) {
                throw DocumentRefusal.DOCUMENT_TEMPLATE_TEXT_TOO_LONG.raise(RefusalDetail.text(named));
            }
            boolean namesAnswer = PlaceholderTokens.keysIn(suggestion).stream()
                    .map(written -> PlaceholderKey.parse(written).base())
                    .anyMatch(TemplateQuestion::names);
            if (namesAnswer) {
                throw DocumentRefusal.DOCUMENT_TEMPLATE_QUESTION_IN_SUGGESTION.raise(RefusalDetail.text(named));
            }
            var kind = Objects.requireNonNullElse(question.kind(), TemplateQuestionKind.TEXT);
            var limits = AnswerLimits.checked(kind, question.limits(), named);
            out.add(new TemplateQuestion(key, label, kind, suggestion, question.required(), limits));
        }
        return List.copyOf(out);
    }

    private static String strip(@Nullable String text) {
        return text == null ? "" : text.strip();
    }
}
