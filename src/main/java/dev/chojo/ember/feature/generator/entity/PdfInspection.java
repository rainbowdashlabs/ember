/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.entity;

import dev.chojo.ember.feature.question.QuestionConfigs;

import java.util.List;
import java.util.Optional;

/**
 * What an uploaded PDF holds that fields care about, read once when it is uploaded.
 *
 * @param pages      its pages, the first first
 * @param formFields the form fields it brings, in the order the PDF lists them
 */
public record PdfInspection(List<PdfPage> pages, List<FormField> formFields) {
    private static final PdfInspection EMPTY = new PdfInspection(List.of(), List.of());

    public PdfInspection {
        pages = pages == null ? List.of() : List.copyOf(pages);
        formFields = formFields == null ? List.of() : List.copyOf(formFields);
    }

    /**
     * @param number a page, counted from one
     * @return that page, or empty where the PDF has no such page
     */
    public Optional<PdfPage> page(int number) {
        return number >= 1 && number <= pages.size() ? Optional.of(pages.get(number - 1)) : Optional.empty();
    }

    /**
     * @param name the fully qualified name of a form field
     * @return the form field of that name, or empty where the PDF has none
     */
    public Optional<FormField> formField(String name) {
        return formFields.stream().filter(field -> field.name().equals(name)).findFirst();
    }

    /**
     * Reads the stored column.
     *
     * @param json the column
     * @return the inspection, an empty one where the column says nothing readable
     */
    public static PdfInspection parse(String json) {
        return QuestionConfigs.parse(json, PdfInspection.class, EMPTY);
    }

    /** @return the inspection as it is stored */
    public String toJson() {
        return QuestionConfigs.toJson(this);
    }
}
