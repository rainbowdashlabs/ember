/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.service;

import dev.chojo.ember.api.refusal.DocumentRefusal;
import dev.chojo.ember.api.refusal.RefusalDetail;
import dev.chojo.ember.feature.generator.entity.QuestionLimits;
import dev.chojo.ember.feature.generator.entity.QuestionLimits.DayBound;
import dev.chojo.ember.feature.generator.entity.TemplateQuestion;
import dev.chojo.ember.feature.generator.entity.TemplateQuestionKind;
import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * The limits of a question: checked where a template defines them, and applied to an answer.
 *
 * <p>A number is taken with a point or a comma before its fraction and kept as a plain decimal. A choice
 * is taken in any case and kept as the entry is written.
 */
final class AnswerLimits {
    /** How far a bound of a date question reaches from the day of generation, either way. */
    static final int MAX_DAYS_FROM_TODAY = 36_500;

    private static final Pattern NUMBER = Pattern.compile("-?\\d{1,15}(?:[.,]\\d{1,6})?");

    private AnswerLimits() {}

    /**
     * The limits a question of a kind keeps, those of other kinds dropped.
     *
     * @param kind  the kind of the question
     * @param raw   the limits as the editor sent them
     * @param named how a refusal names the question
     * @return the limits the question keeps
     */
    static QuestionLimits checked(TemplateQuestionKind kind, QuestionLimits raw, String named) {
        return switch (kind) {
            case TEXT -> checkedText(raw, named);
            case DATE -> checkedDays(raw, named);
            case NUMBER -> checkedNumber(raw, named);
            case CHOICE -> QuestionLimits.choice(checkedChoices(raw.choices(), named));
            case CHECK -> QuestionLimits.NONE;
        };
    }

    /**
     * @param text an answer to a number question as it was written
     * @return the number it names, or empty where it names none
     */
    static Optional<BigDecimal> number(String text) {
        if (!NUMBER.matcher(text).matches()) return Optional.empty();
        return Optional.of(new BigDecimal(text.replace(',', '.')));
    }

    /**
     * @param number a number
     * @return how an answer keeps it
     */
    static String kept(BigDecimal number) {
        return number.stripTrailingZeros().toPlainString();
    }

    /**
     * @param question a choice question
     * @param text     an answer as it was written
     * @return the entry it names, as the question writes it, or empty where it names none
     */
    static Optional<String> choice(TemplateQuestion question, String text) {
        return question.limits().choices().stream()
                .filter(entry -> entry.equalsIgnoreCase(text))
                .findFirst();
    }

    /**
     * What an answer breaks of its question's limits.
     *
     * @param question the question
     * @param answer   the answer as it is kept, not empty
     * @param today    the day the document is generated
     * @return the refusal for the limit the answer breaks, or empty where it keeps them all
     */
    static Optional<DocumentRefusal> broken(TemplateQuestion question, String answer, LocalDate today) {
        var limits = question.limits();
        return switch (question.kind()) {
            case TEXT -> brokenText(limits, answer);
            case DATE -> brokenDay(limits, LocalDate.parse(answer), today);
            case NUMBER -> brokenNumber(limits, new BigDecimal(answer));
            case CHOICE, CHECK -> Optional.empty();
        };
    }

    private static Optional<DocumentRefusal> brokenText(QuestionLimits limits, String answer) {
        var maxLength = limits.maxLength();
        if (maxLength != null && answer.length() > maxLength) {
            return Optional.of(DocumentRefusal.DOCUMENT_ANSWER_TOO_LONG);
        }
        var format = limits.format();
        if (format != null && !format.matches(answer)) {
            return Optional.of(DocumentRefusal.DOCUMENT_ANSWER_WRONG_FORMAT);
        }
        return Optional.empty();
    }

    private static Optional<DocumentRefusal> brokenDay(QuestionLimits limits, LocalDate day, LocalDate today) {
        var earliestBound = limits.earliest();
        var latestBound = limits.latest();
        var earliest = earliestBound == null ? null : earliestBound.on(today);
        var latest = latestBound == null ? null : latestBound.on(today);
        boolean outside = (earliest != null && day.isBefore(earliest)) || (latest != null && day.isAfter(latest));
        return outside ? Optional.of(DocumentRefusal.DOCUMENT_ANSWER_DAY_OUT_OF_RANGE) : Optional.empty();
    }

    private static Optional<DocumentRefusal> brokenNumber(QuestionLimits limits, BigDecimal number) {
        var min = limits.min();
        var max = limits.max();
        boolean outside = (min != null && number.compareTo(min) < 0)
                || (max != null && number.compareTo(max) > 0)
                || (limits.whole() && number.stripTrailingZeros().scale() > 0);
        return outside ? Optional.of(DocumentRefusal.DOCUMENT_ANSWER_NUMBER_OUT_OF_RANGE) : Optional.empty();
    }

    private static QuestionLimits checkedText(QuestionLimits raw, String named) {
        var maxLength = raw.maxLength();
        if (maxLength != null && (maxLength < 1 || maxLength > QuestionLimits.MAX_LENGTH)) throw invalid(named);
        return QuestionLimits.text(maxLength, raw.format());
    }

    private static QuestionLimits checkedDays(QuestionLimits raw, String named) {
        var earliest = checkedBound(raw.earliest(), named);
        var latest = checkedBound(raw.latest(), named);
        if (earliest != null && latest != null && after(earliest, latest)) throw invalid(named);
        return QuestionLimits.days(earliest, latest);
    }

    private static @Nullable DayBound checkedBound(@Nullable DayBound bound, String named) {
        if (bound == null) return null;
        var day = bound.day();
        var fromToday = bound.fromToday();
        if (day == null && fromToday == null) return null;
        if (day != null && fromToday != null) throw invalid(named);
        if (fromToday != null && Math.abs(fromToday) > MAX_DAYS_FROM_TODAY) throw invalid(named);
        return bound;
    }

    /** Whether one bound lies after the other on every day, which only bounds of the same sort can say. */
    private static boolean after(DayBound earliest, DayBound latest) {
        var earliestDay = earliest.day();
        var latestDay = latest.day();
        if (earliestDay != null && latestDay != null) return earliestDay.isAfter(latestDay);
        var earliestFromToday = earliest.fromToday();
        var latestFromToday = latest.fromToday();
        if (earliestFromToday != null && latestFromToday != null) return earliestFromToday > latestFromToday;
        return false;
    }

    private static QuestionLimits checkedNumber(QuestionLimits raw, String named) {
        var min = raw.min();
        var max = raw.max();
        if (min != null && max != null && min.compareTo(max) > 0) throw invalid(named);
        return QuestionLimits.number(min, max, raw.whole());
    }

    private static List<String> checkedChoices(List<String> raw, String named) {
        var seen = new HashSet<String>();
        var out = new ArrayList<String>();
        for (var entry : raw) {
            String choice = entry == null ? "" : entry.strip();
            boolean fresh = seen.add(choice.toLowerCase(Locale.ROOT));
            if (choice.isEmpty() || choice.length() > QuestionLimits.MAX_CHOICE || !fresh) throw choicesInvalid(named);
            out.add(choice);
        }
        if (out.isEmpty() || out.size() > QuestionLimits.MAX_CHOICES) throw choicesInvalid(named);
        return List.copyOf(out);
    }

    private static RuntimeException invalid(String named) {
        return DocumentRefusal.DOCUMENT_TEMPLATE_QUESTION_LIMITS_INVALID.raise(RefusalDetail.text(named));
    }

    private static RuntimeException choicesInvalid(String named) {
        return DocumentRefusal.DOCUMENT_TEMPLATE_QUESTION_CHOICES_INVALID.raise(RefusalDetail.text(named));
    }
}
