/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.service;

import dev.chojo.ember.api.refusal.DocumentRefusal;
import dev.chojo.ember.api.refusal.RefusalDetail;
import dev.chojo.ember.feature.generator.entity.DateFormat;
import dev.chojo.ember.feature.generator.entity.DateKind;
import dev.chojo.ember.feature.generator.entity.DocumentLanguage;
import dev.chojo.ember.feature.generator.entity.PlaceholderKey;
import dev.chojo.ember.feature.generator.entity.PlaceholderTokens;
import dev.chojo.ember.feature.generator.entity.ResolvedValues;
import dev.chojo.ember.feature.generator.entity.TemplateQuestion;
import dev.chojo.ember.feature.generator.service.pdf.YesWords;
import org.jspecify.annotations.Nullable;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The answers to the questions of a template for one document, and what they print as.
 *
 * <p>An answer is kept the way it is sent: a text as written, a day as an ISO date, a tick as
 * {@code true} or {@code false}. A question the person generating did not answer takes its suggestion,
 * filled in for the member: a text as it reads, a day where the text names one, a tick where it says yes.
 *
 * <p>A text prints as written, a day in the format its placeholder names or as {@code 03.10.2026}, and a
 * tick as a ticked or an empty box, which ticks a check field or a check box ({@link YesWords}) and shows
 * as a box in a letter. A required question without an answer is a missing value; any other prints
 * empty.
 */
final class QuestionAnswers {
    /** What a ticked check prints as. */
    static final String TICKED = "☒";

    /** What a check left empty prints as. */
    static final String EMPTY_BOX = "☐";

    private static final DateTimeFormatter WRITTEN_DAY = DateTimeFormatter.ofPattern("d.M.uuuu");

    private QuestionAnswers() {}

    /**
     * @param keys the keys a template names, a date with its format
     * @return those that name the answer to a question
     */
    static Set<String> askedIn(Collection<String> keys) {
        return keys.stream()
                .filter(key -> TemplateQuestion.names(PlaceholderKey.parse(key).base()))
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /**
     * @param questions the questions of a template
     * @return the keys their suggestions name
     */
    static Set<String> suggestionKeys(List<TemplateQuestion> questions) {
        return questions.stream()
                .flatMap(question -> PlaceholderTokens.keysIn(question.suggestion()).stream())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /**
     * The answer each question suggests for one member.
     *
     * @param questions the questions of a template
     * @param values    the values of the keys the suggestions name, for the member
     * @return the suggested answer of every question by its key, as an answer is sent
     */
    static Map<String, String> suggested(List<TemplateQuestion> questions, Map<String, String> values) {
        var out = new LinkedHashMap<String, String>();
        for (var question : questions) {
            String filled =
                    PlaceholderTokens.fill(question.suggestion(), values).strip();
            out.put(
                    question.key(),
                    switch (question.kind()) {
                        case TEXT -> filled;
                        case DATE -> day(filled).map(LocalDate::toString).orElse("");
                        case CHECK -> String.valueOf(YesWords.saysYes(filled));
                    });
        }
        return out;
    }

    /**
     * The answers a document is drawn with: what was given, and the suggestion of every question nobody
     * answered. An answer to no question of the template is left out.
     *
     * @param questions the questions of a template
     * @param given     what the person generating answered, by the key of the question
     * @param suggested what each question suggests, by its key
     * @return the answers, in the order the questions are asked
     */
    static Map<String, String> answers(
            List<TemplateQuestion> questions, Map<String, String> given, Map<String, String> suggested) {
        var out = new LinkedHashMap<String, String>();
        for (var question : questions) {
            String answer = given.containsKey(question.key())
                    ? normalized(question, given.get(question.key()))
                    : suggested.getOrDefault(question.key(), "");
            out.put(question.key(), answer);
        }
        return out;
    }

    /**
     * Adds what the answers print as to the values of a document.
     *
     * @param resolved  the values of every other key
     * @param keys      every key the template names, in template order
     * @param questions the questions of the template
     * @param answers   the answers, by the key of the question
     * @param language  the language the template writes in
     * @return the values with the answers among them, a required question without an answer missing
     */
    static ResolvedValues printed(
            ResolvedValues resolved,
            Collection<String> keys,
            List<TemplateQuestion> questions,
            Map<String, String> answers,
            DocumentLanguage language) {
        var byKey =
                questions.stream().collect(Collectors.toMap(TemplateQuestion::placeholderKey, question -> question));
        var values = new LinkedHashMap<String, String>();
        var missing = new ArrayList<String>();
        for (String key : keys) {
            var written = PlaceholderKey.parse(key);
            var question = byKey.get(written.base());
            if (question == null) {
                String value = resolved.values().get(key);
                if (value != null) values.put(key, value);
                else if (resolved.missing().contains(key)) missing.add(key);
                continue;
            }
            String printed = print(question, answers.getOrDefault(question.key(), ""), written.format(), language);
            if (printed.isEmpty() && question.required()) {
                missing.add(key);
            } else {
                values.put(key, printed);
            }
        }
        return new ResolvedValues(values, missing, resolved.subjects());
    }

    private static String print(
            TemplateQuestion question, String answer, @Nullable String format, DocumentLanguage language) {
        return switch (question.kind()) {
            case TEXT -> answer;
            case CHECK -> Boolean.parseBoolean(answer) ? TICKED : EMPTY_BOX;
            case DATE ->
                day(answer)
                        .flatMap(day -> (format == null
                                        ? Optional.of(DateFormat.of(DateKind.DATE.standard()))
                                        : DateFormat.of(format))
                                .flatMap(printed -> printed.format(day, language)))
                        .orElse("");
        };
    }

    /**
     * An answer as it was sent, checked: a day has to name one, a tick is true or false.
     */
    private static String normalized(TemplateQuestion question, @Nullable String raw) {
        String answer = raw == null ? "" : raw.strip();
        return switch (question.kind()) {
            case TEXT -> answer;
            case CHECK -> String.valueOf(Boolean.parseBoolean(answer));
            case DATE -> {
                if (answer.isEmpty()) yield "";
                yield day(answer)
                        .map(LocalDate::toString)
                        .orElseThrow(() ->
                                DocumentRefusal.DOCUMENT_ANSWER_NOT_A_DAY.raise(RefusalDetail.text(question.label())));
            }
        };
    }

    /** The day a text names: an ISO date, or a day written as {@code 3.10.2026}. */
    private static Optional<LocalDate> day(String text) {
        if (text.isEmpty()) return Optional.empty();
        try {
            return Optional.of(LocalDate.parse(text));
        } catch (DateTimeException notIso) {
            try {
                return Optional.of(LocalDate.parse(text, WRITTEN_DAY));
            } catch (DateTimeException notWritten) {
                return Optional.empty();
            }
        }
    }
}
