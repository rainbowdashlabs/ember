/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.form.service;

import dev.chojo.ember.feature.form.entity.FormAnswerValue;
import dev.chojo.ember.feature.form.entity.FormPage;
import dev.chojo.ember.feature.form.entity.FormQuestion;
import dev.chojo.ember.feature.form.entity.FormQuestionConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Stored answers as the line a person reads, the server's counterpart of the answer display of the
 * results screen.
 *
 * <p>Answers name options by their key, which means nothing to whoever opens an export. Each key is
 * written as its option's label instead; a key the question no longer has is written as itself
 * rather than left out, so a spreadsheet never shows less than was answered.
 */
public final class FormAnswerText {
    private FormAnswerText() {}

    /**
     * One answer as text.
     *
     * @param question the question answered
     * @param value    the answer, possibly none
     * @param language the language of the export, {@code en} or anything else for German
     * @return the answer as one line, empty where there is none
     */
    public static String of(FormQuestion question, FormAnswerValue value, String language) {
        if (value == null) return "";
        var labels = labels(question.config());
        return switch (value) {
            case FormAnswerValue.Text(String text) -> text == null ? "" : text;
            case FormAnswerValue.DateValue(String date) -> date == null ? "" : date;
            case FormAnswerValue.Rating(int rating) -> rating < 1 ? "" : String.valueOf(rating);
            case FormAnswerValue.Choice(List<String> selected, String other) ->
                choice(selected, other, labels, language);
            case FormAnswerValue.Ranking(List<String> order) -> ranking(order, labels);
            case FormAnswerValue.Likert(Map<String, Integer> ratings) -> likert(question.config(), ratings);
        };
    }

    /**
     * The pages a response went through, each by its title or, where it has none, by its number.
     *
     * @param path     the keys of the pages visited
     * @param pages    the form's pages as they stand now
     * @param language the language of the export
     * @return the pages, one after another
     */
    public static String path(List<String> path, List<FormPage> pages, String language) {
        var names = new ArrayList<String>(path.size());
        for (var key : path) {
            names.add(pages.stream()
                    .filter(page -> page.key().equals(key))
                    .findFirst()
                    .map(page -> pageName(page, pages.indexOf(page), language))
                    .orElse(key));
        }
        return String.join(", ", names);
    }

    private static String pageName(FormPage page, int index, String language) {
        if (page.title() != null && !page.title().isBlank()) return page.title();
        return ("en".equals(language) ? "Page " : "Seite ") + (index + 1);
    }

    private static Map<String, String> labels(FormQuestionConfig config) {
        return config.keyedOptions().stream()
                .collect(Collectors.toMap(
                        FormQuestionConfig.Option::key, FormQuestionConfig.Option::label, (a, b) -> a));
    }

    private static String choice(List<String> selected, String other, Map<String, String> labels, String language) {
        var parts = new ArrayList<String>();
        if (selected != null) selected.stream().map(label(labels)).forEach(parts::add);
        if (other != null && !other.isBlank()) parts.add(("en".equals(language) ? "Other: " : "Sonstige: ") + other);
        return String.join(", ", parts);
    }

    private static String ranking(List<String> order, Map<String, String> labels) {
        if (order == null) return "";
        var parts = new ArrayList<String>(order.size());
        for (int rank = 0; rank < order.size(); rank++) {
            parts.add((rank + 1) + ". " + label(labels).apply(order.get(rank)));
        }
        return String.join(", ", parts);
    }

    private static String likert(FormQuestionConfig config, Map<String, Integer> ratings) {
        if (ratings == null) return "";
        var parts = new ArrayList<String>();
        for (var statement : config.keyedOptions()) {
            var rating = ratings.get(statement.key());
            if (rating != null) parts.add(statement.label() + ": " + rating);
        }
        return String.join(", ", parts);
    }

    private static Function<String, String> label(Map<String, String> labels) {
        return key -> labels.getOrDefault(key, key);
    }
}
