/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.quiz.service;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeName;
import dev.chojo.ember.feature.quiz.entity.QuestionConfig;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.IntFunction;

/**
 * What a printed test shows below a question's title: the part that differs per question type,
 * as plain values for the sheet template. The template reads {@code kind} to pick the layout.
 *
 * <p>The question sheet shuffles what a reader could otherwise copy from the order (choices, the
 * right-hand side of a pairing, the steps to order and the word bank); the solution sheet keeps
 * the order the question was written in.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "kind")
public sealed interface QuizSheetBody {
    int DEFAULT_LINES = 3;
    int DEFAULT_REQUIRED_COUNT = 3;
    double MIN_GAP_WIDTH_CM = 1.5;

    /**
     * Builds the body for a question from its typed config.
     *
     * @param config the question's config; an unreadable one prints nothing below the title
     * @param withAnswers whether the body is for the solution sheet
     */
    static QuizSheetBody of(QuestionConfig config, boolean withAnswers) {
        return switch (config) {
            case QuestionConfig.MultipleChoice choice -> Choice.of(choice, withAnswers);
            case QuestionConfig.TrueFalse trueFalse -> new TrueFalse(trueFalse.correctAnswer());
            case QuestionConfig.FreeAnswer free ->
                new FreeAnswer(texts(free.answers()), free.lines() > 0 ? free.lines() : DEFAULT_LINES);
            case QuestionConfig.FillInTheBlank blanks -> Gaps.of(blanks, withAnswers);
            case QuestionConfig.Connect connect -> Connect.of(connect, withAnswers);
            case QuestionConfig.Ordering ordering -> new Ordering(shuffledUnless(withAnswers, texts(ordering.items())));
            case QuestionConfig.ImageText imageText -> new ImageText(text(imageText.answer()));
            case QuestionConfig.Enumeration enumeration ->
                new Enumeration(
                        texts(enumeration.answers()),
                        enumeration.requiredCount() > 0 ? enumeration.requiredCount() : DEFAULT_REQUIRED_COUNT,
                        enumeration.orderedRequired());
            case QuestionConfig.Unknown _ -> new None();
        };
    }

    private static <T> List<T> orEmpty(List<T> list) {
        return list == null ? List.of() : list;
    }

    private static String text(String value) {
        return value == null ? "" : value;
    }

    private static List<String> texts(List<String> values) {
        return orEmpty(values).stream().map(QuizSheetBody::text).toList();
    }

    private static <T> List<T> shuffledUnless(boolean keepOrder, List<T> list) {
        var copy = new ArrayList<>(orEmpty(list));
        if (!keepOrder) Collections.shuffle(copy);
        return copy;
    }

    /** Choices to tick, with the correct ones marked on the solution sheet. */
    @JsonTypeName("choice")
    record Choice(List<Option> options) implements QuizSheetBody {
        static Choice of(QuestionConfig.MultipleChoice config, boolean withAnswers) {
            return new Choice(shuffledUnless(
                    withAnswers,
                    orEmpty(config.options()).stream()
                            .map(option -> new Option(text(option.text()), option.correct()))
                            .toList()));
        }

        public record Option(String text, boolean correct) {}
    }

    /** A statement to mark as true or false. */
    @JsonTypeName("trueFalse")
    record TrueFalse(boolean correct) implements QuizSheetBody {}

    /** Ruled lines to write on, or the accepted answers on the solution sheet. */
    @JsonTypeName("freeAnswer")
    record FreeAnswer(List<String> answers, int lines) implements QuizSheetBody {}

    /**
     * A text with gaps.
     *
     * @param words the word bank the question sheet offers, shuffled, or empty without one
     * @param parts the text split into its words and its gaps, or empty when the question has no text
     * @param answers the answers in gap order, listed on the solution sheet when there is no text
     * @param gapCount how many bare lines the question sheet draws when there is no text to gap
     */
    @JsonTypeName("gaps")
    record Gaps(List<String> words, List<Part> parts, List<String> answers, int gapCount) implements QuizSheetBody {
        static Gaps of(QuestionConfig.FillInTheBlank config, boolean withAnswers) {
            List<String> answers = texts(config.answers());
            List<String> distractors = texts(config.distractors());
            boolean hasText = config.text() != null && !config.text().isBlank();
            boolean hasAnswers = config.answers() != null;
            boolean wordBank = !distractors.isEmpty();
            var words = new ArrayList<>(answers);
            words.addAll(distractors);

            if (withAnswers) {
                if (!hasText || !hasAnswers) return new Gaps(List.of(), List.of(), answers, 0);
                return new Gaps(
                        List.of(),
                        split(config.text(), index -> {
                            String answer = index < answers.size() ? answers.get(index) : "?";
                            Integer number = wordBank ? words.indexOf(answer) + 1 : null;
                            return Part.gap(answer, number, MIN_GAP_WIDTH_CM);
                        }),
                        answers,
                        0);
            }
            if (wordBank) {
                Collections.shuffle(words);
                List<Part> parts =
                        hasText ? split(config.text(), _ -> Part.gap(null, null, MIN_GAP_WIDTH_CM)) : List.of();
                return new Gaps(words, parts, answers, 0);
            }
            if (hasText && hasAnswers) {
                return new Gaps(
                        List.of(),
                        split(config.text(), index -> Part.gap(null, null, widthFor(answers, index))),
                        answers,
                        0);
            }
            return new Gaps(List.of(), List.of(), answers, hasAnswers ? answers.size() : 1);
        }

        /** Room for the answer and a little more, and never less than a short word needs. */
        private static double widthFor(List<String> answers, int index) {
            int length = index < answers.size() ? answers.get(index).length() : 5;
            return Math.max(MIN_GAP_WIDTH_CM, (length + 2) * 0.25);
        }

        /** Splits a text at every run of two or more underscores, asking {@code gap} for each gap in turn. */
        static List<Part> split(String text, IntFunction<Part> gap) {
            var parts = new ArrayList<Part>();
            var segment = new StringBuilder();
            int gapIndex = 0;
            for (int i = 0; i < text.length(); i++) {
                if (text.charAt(i) == '_' && i + 1 < text.length() && text.charAt(i + 1) == '_') {
                    if (!segment.isEmpty()) parts.add(Part.text(segment.toString()));
                    segment.setLength(0);
                    while (i + 1 < text.length() && text.charAt(i + 1) == '_') i++;
                    parts.add(gap.apply(gapIndex++));
                } else {
                    segment.append(text.charAt(i));
                }
            }
            if (!segment.isEmpty()) parts.add(Part.text(segment.toString()));
            return parts;
        }

        /**
         * One piece of a gapped text.
         *
         * @param gap whether this piece is a gap rather than text
         * @param text the text, or for a gap on the solution sheet the answer that fills it
         * @param number the gap's word in the word bank, or {@code null} without one
         * @param width how wide the question sheet draws the gap, in centimetres
         */
        public record Part(boolean gap, String text, Integer number, double width) {
            static Part text(String text) {
                return new Part(false, text, null, 0);
            }

            static Part gap(String answer, Integer number, double width) {
                return new Part(true, answer, number, width);
            }
        }
    }

    /** Terms to pair up; the question sheet shuffles the right-hand side. */
    @JsonTypeName("connect")
    record Connect(List<Pair> pairs) implements QuizSheetBody {
        static Connect of(QuestionConfig.Connect config, boolean withAnswers) {
            var pairs = orEmpty(config.pairs());
            var left =
                    texts(pairs.stream().map(QuestionConfig.Connect.Pair::left).toList());
            var right = shuffledUnless(
                    withAnswers,
                    texts(pairs.stream().map(QuestionConfig.Connect.Pair::right).toList()));
            var rows = new ArrayList<Pair>();
            for (int i = 0; i < pairs.size(); i++) rows.add(new Pair(left.get(i), right.get(i)));
            return new Connect(rows);
        }

        public record Pair(String left, String right) {}
    }

    /** Steps to put in order, shuffled on the question sheet and in order on the solution sheet. */
    @JsonTypeName("ordering")
    record Ordering(List<String> items) implements QuizSheetBody {}

    /** A picture to describe, with the expected answer on the solution sheet. */
    @JsonTypeName("imageText")
    record ImageText(String answer) implements QuizSheetBody {}

    /**
     * A number of things to name.
     *
     * @param answers the accepted answers, listed on the solution sheet
     * @param requiredCount how many the question asks for, one line each on the question sheet
     * @param ordered whether the order of the answers counts
     */
    @JsonTypeName("enumeration")
    record Enumeration(List<String> answers, int requiredCount, boolean ordered) implements QuizSheetBody {}

    /** Nothing below the title, for a question whose config could not be read. */
    @JsonTypeName("none")
    record None() implements QuizSheetBody {}
}
