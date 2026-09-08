/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.question;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

/**
 * The one place an answer is measured against the question it answers.
 *
 * <p>Six features asked questions and three of them checked nothing at all, so a station could
 * record a shirt size the appointment does not offer, a birth date that is not a date, and a number
 * of guests outside the range printed next to the box. What each one accepted was whatever its own
 * screen happened to send.
 *
 * <p>The same rules measure a default where a question is configured, which is what stops a sheet
 * being set up to start every answer at a value the question would later refuse.
 */
public final class QuestionCheck {

    private QuestionCheck() {}

    /**
     * Whether this answer is one the question takes.
     *
     * <p>An unanswered question falls back to its default before anything else, which is why a
     * required question with a default is answered by having one.
     *
     * @param question what is being answered
     * @param answer   what was given, which may be nothing
     * @return what is wrong with it, or empty where nothing is
     */
    public static Optional<QuestionProblem> answer(Question question, String answer) {
        String value = said(answer);
        if (value.isEmpty()) value = said(question.defaultValue());
        if (value.isEmpty()) {
            return question.required()
                    ? Optional.of(new QuestionProblem(QuestionProblem.Code.REQUIRED, question.name(), null))
                    : Optional.empty();
        }
        return value(question, value);
    }

    /**
     * Whether what was answered is one the question takes, without asking whether it had to be
     * answered at all.
     *
     * <p>What a half-filled sheet wants. An attendance sheet is written on through the evening and
     * saved as it goes, so demanding every required answer at every save would refuse the sheet
     * itself; what it can still say is that a date is a date.
     *
     * @param question what is being answered
     * @param answer   what was given, which may be nothing
     * @return what is wrong with it, or empty where nothing is or nothing was given
     */
    public static Optional<QuestionProblem> answerIfGiven(Question question, String answer) {
        String value = said(answer);
        return value.isEmpty() ? Optional.empty() : value(question, value);
    }

    /**
     * Whether the question's own default is an answer it would take.
     *
     * <p>Asked where a question is written rather than where it is answered: a default outside the
     * options is a mistake made once, by whoever configured it, and refusing it there is the only
     * place anybody can still put it right.
     *
     * @param question the question as it is being configured
     * @return what is wrong with its default, or empty where it has none or it is fine
     */
    public static Optional<QuestionProblem> defaultValue(Question question) {
        String value = said(question.defaultValue());
        return value.isEmpty() ? Optional.empty() : value(question, value);
    }

    private static Optional<QuestionProblem> value(Question question, String value) {
        return switch (question.kind()) {
            case TEXT, LONG_TEXT -> Optional.empty();
            case NUMBER -> number(question, value, true);
            case DECIMAL -> number(question, value, false);
            case CHOICE -> choice(question, value);
            case DATE -> parses(question, value, LocalDate::parse, QuestionProblem.Code.NOT_A_DATE);
            case TIME -> parses(question, value, LocalTime::parse, QuestionProblem.Code.NOT_A_TIME);
            case BOOLEAN -> boolish(question, value);
            case URL -> url(question, value);
            case MEMBER, MEMBER_LIST -> members(question, value);
        };
    }

    /**
     * A number, and where the question says so, one that sits between its bounds.
     *
     * @param whole whether a fraction is refused, which is what tells a count of people from a length
     */
    private static Optional<QuestionProblem> number(Question question, String value, boolean whole) {
        BigDecimal number;
        try {
            number = new BigDecimal(value.replace(',', '.'));
        } catch (NumberFormatException e) {
            return problem(QuestionProblem.Code.NOT_A_NUMBER, question, null);
        }
        if (whole && number.stripTrailingZeros().scale() > 0) {
            return problem(QuestionProblem.Code.NOT_A_NUMBER, question, null);
        }
        if (!(question.rules() instanceof QuestionRules.Bounds(BigDecimal min, BigDecimal max))) {
            return Optional.empty();
        }
        if (min != null && number.compareTo(min) < 0) {
            return problem(QuestionProblem.Code.OUT_OF_RANGE, question, "of at least " + min.toPlainString());
        }
        if (max != null && number.compareTo(max) > 0) {
            return problem(QuestionProblem.Code.OUT_OF_RANGE, question, "of at most " + max.toPlainString());
        }
        return Optional.empty();
    }

    private static Optional<QuestionProblem> choice(Question question, String value) {
        if (!(question.rules() instanceof QuestionRules.Choice(List<String> options))) return Optional.empty();
        if (options.isEmpty() || options.contains(value)) return Optional.empty();
        return problem(QuestionProblem.Code.NOT_AN_OPTION, question, value);
    }

    private static Optional<QuestionProblem> boolish(Question question, String value) {
        boolean known = value.equalsIgnoreCase("true")
                || value.equalsIgnoreCase("false")
                || value.equals("1")
                || value.equals("0");
        return known ? Optional.empty() : problem(QuestionProblem.Code.NOT_A_BOOLEAN, question, null);
    }

    /**
     * A web address is one somebody can follow, which is what makes the scheme part of the check: a
     * link nobody can open is worse than an empty field, because it looks answered.
     */
    private static Optional<QuestionProblem> url(Question question, String value) {
        String lower = value.toLowerCase();
        boolean followable = (lower.startsWith("http://") || lower.startsWith("https://")) && value.length() > 8;
        return followable ? Optional.empty() : problem(QuestionProblem.Code.NOT_A_URL, question, null);
    }

    private static Optional<QuestionProblem> members(Question question, String value) {
        if (!QuestionValues.namesOnlyMembers(value)) {
            return problem(QuestionProblem.Code.NOT_A_MEMBER, question, value);
        }
        if (!question.kind().namesSeveralMembers()
                && QuestionValues.memberIds(value).size() > 1) {
            return problem(QuestionProblem.Code.NOT_A_MEMBER, question, value);
        }
        return Optional.empty();
    }

    private static Optional<QuestionProblem> parses(
            Question question, String value, Parser parser, QuestionProblem.Code code) {
        try {
            parser.parse(value);
            return Optional.empty();
        } catch (Exception e) {
            return problem(code, question, null);
        }
    }

    private static Optional<QuestionProblem> problem(QuestionProblem.Code code, Question question, String detail) {
        return Optional.of(new QuestionProblem(code, question.name(), detail));
    }

    /**
     * What was actually said, which is nothing in more spellings than one.
     *
     * <p>The features that keep their answers as JSON write an unanswered question as the literal
     * {@code null} or as an empty string in quotes, and both mean the same as an empty box.
     */
    private static String said(String stored) {
        String text = QuestionValues.text(stored);
        return text.equalsIgnoreCase("null") ? "" : text;
    }

    /** What reading a date or a time out of an answer looks like, so both go through one path. */
    private interface Parser {
        void parse(String value);
    }
}
