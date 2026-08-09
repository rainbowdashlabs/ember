/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.quiz.entity;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * Answer-free projection of a {@link QuestionConfig}, served to members who may take a
 * test but must not see the solution. One variant per question type; the variant carries
 * exactly the fields the question renderer needs and nothing that would reveal the
 * correct answer.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public sealed interface QuizQuestionConfigView {

    /**
     * Config for types whose renderer needs nothing beyond the question itself.
     */
    record Empty() implements QuizQuestionConfigView {}

    /**
     * @param options     the selectable options, stripped of their correct marker
     * @param multiSelect whether more than one option is correct, so the renderer can
     *                    offer checkboxes instead of radio buttons
     */
    record MultipleChoice(List<Option> options, boolean multiSelect) implements QuizQuestionConfigView {

        /**
         * @param text the option label
         */
        public record Option(String text) {}
    }

    /**
     * @param lines the number of answer lines to render
     */
    record FreeAnswer(int lines) implements QuizQuestionConfigView {}

    /**
     * @param text        the gapped text
     * @param wordBank    every word offered for the gaps, correct answers and distractors
     *                    mixed together
     * @param gapCount    the number of gaps to fill
     * @param useDropdown whether gaps are filled from a dropdown instead of typed
     */
    record FillInTheBlank(String text, List<String> wordBank, int gapCount, boolean useDropdown)
            implements QuizQuestionConfigView {}

    /**
     * @param leftItems  the items to connect from
     * @param rightItems the items to connect to, in config order — the renderer shuffles
     *                   them for display
     */
    record Connect(List<String> leftItems, List<String> rightItems) implements QuizQuestionConfigView {}

    /**
     * @param items the items to bring into the right order
     */
    record Ordering(List<String> items) implements QuizQuestionConfigView {}

    /**
     * @param requiredCount how many entries the member has to name
     */
    record Enumeration(int requiredCount) implements QuizQuestionConfigView {}
}
