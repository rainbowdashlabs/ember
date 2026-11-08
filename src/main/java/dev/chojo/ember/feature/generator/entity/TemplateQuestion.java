/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Something a template asks the person generating a document, which the template names as
 * {@code {{ask.<key>}}}.
 *
 * <p>The suggestion is a text with placeholders, filled in for the member the document is about. It
 * prefills the answer for a manager generating one document, who may change it, and it is the answer
 * wherever nobody is asked: in self service, in a run over many members and on an appointment's copy.
 * The suggestion of a check ticks it where it says yes ({@code YesWords}), so a check can follow
 * the member's data and still be ticked or cleared by hand.
 *
 * <p>An answer outside the limits is refused. A suggestion outside them counts as no answer, so a run
 * over many members is not stopped by one member's data.
 *
 * @param key        the name the template names the answer by, letters, digits and underscores
 * @param label      what the person generating is asked
 * @param kind       what the answer is
 * @param suggestion the text with placeholders that suggests the answer, empty for none
 * @param required   whether an empty answer counts as a missing value
 * @param limits     what counts as an answer
 */
public record TemplateQuestion(
        String key,
        String label,
        TemplateQuestionKind kind,
        String suggestion,
        boolean required,
        QuestionLimits limits) {

    /** What a template writes before the key of a question to name its answer. */
    public static final String PREFIX = "ask.";

    /** How the key of a question is written. */
    public static final Pattern KEY = Pattern.compile("[A-Za-z0-9_]{1,40}");

    /** The columns {@link #map()} reads, in a form a query can splice in. */
    public static final String COLUMNS = "key, label, kind, suggestion, required, limits";

    public TemplateQuestion {
        limits = limits == null ? QuestionLimits.NONE : limits;
    }

    /**
     * A question without limits.
     *
     * @param key        the name the template names the answer by
     * @param label      what the person generating is asked
     * @param kind       what the answer is
     * @param suggestion the text with placeholders that suggests the answer, empty for none
     * @param required   whether an empty answer counts as a missing value
     */
    public TemplateQuestion(String key, String label, TemplateQuestionKind kind, String suggestion, boolean required) {
        this(key, label, kind, suggestion, required, QuestionLimits.NONE);
    }

    /**
     * @param limits what counts as an answer
     * @return this question with those limits
     */
    public TemplateQuestion withLimits(QuestionLimits limits) {
        return new TemplateQuestion(key, label, kind, suggestion, required, limits);
    }

    /** @return the key of the placeholder that names the answer */
    public String placeholderKey() {
        return PREFIX + key;
    }

    /**
     * @param placeholderKey the key of a placeholder, without a format
     * @return whether it names the answer to a question
     */
    public static boolean names(String placeholderKey) {
        return placeholderKey.startsWith(PREFIX);
    }

    /**
     * @param questions the questions of a template
     * @return the texts they name placeholders in: their suggestions
     */
    public static List<String> suggestions(List<TemplateQuestion> questions) {
        return questions.stream().map(TemplateQuestion::suggestion).toList();
    }

    public static RowMapping<TemplateQuestion> map() {
        return row -> new TemplateQuestion(
                row.getString("key"),
                row.getString("label"),
                row.getEnum("kind", TemplateQuestionKind.class),
                row.getString("suggestion"),
                row.getBoolean("required"),
                QuestionLimits.parse(row.getString("limits")));
    }
}
