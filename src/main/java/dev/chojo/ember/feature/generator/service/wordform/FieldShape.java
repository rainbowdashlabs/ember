/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.service.wordform;

import dev.chojo.ember.feature.generator.entity.QuestionLimits;
import dev.chojo.ember.feature.generator.service.wordform.FormSpot.FormSpotKind;
import org.jspecify.annotations.Nullable;
import org.w3c.dom.Element;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;

import static dev.chojo.ember.feature.generator.service.wordform.WordXml.W;
import static dev.chojo.ember.feature.generator.service.wordform.WordXml.W14;
import static dev.chojo.ember.feature.generator.service.wordform.WordXml.attribute;
import static dev.chojo.ember.feature.generator.service.wordform.WordXml.children;
import static dev.chojo.ember.feature.generator.service.wordform.WordXml.first;

/**
 * What a place of a Word form takes, as Word itself describes it: a text and the longest it may be, a
 * number, a day, an entry of a list, or a tick.
 *
 * <p>A list keeps the entries a template can take: blank ones, which a content control lists as its
 * prompt, and repeated ones are dropped, a long one is cut and the list ends at the most a question
 * offers. A list with nothing left is a text. A combo box, which Word lets a person type into, is read as
 * a list all the same, since a template's choice takes only its entries.
 *
 * @param kind   what is filled in
 * @param limits what counts as filled in
 */
record FieldShape(FormSpotKind kind, QuestionLimits limits) {

    private static final FieldShape TEXT = new FieldShape(FormSpotKind.TEXT, QuestionLimits.NONE);

    /**
     * @param data the form field data of an old-style field
     * @return what it takes
     */
    static FieldShape ofLegacy(Element data) {
        if (first(data, W, "checkBox") != null) return new FieldShape(FormSpotKind.CHECK, QuestionLimits.NONE);
        var list = first(data, W, "ddList");
        if (list != null) {
            return choice(children(list, W, "listEntry").stream()
                    .map(entry -> attribute(entry, "val"))
                    .toList());
        }
        var text = first(data, W, "textInput");
        return switch (attribute(first(text, W, "type"), "val")) {
            case "number" -> new FieldShape(FormSpotKind.NUMBER, QuestionLimits.NONE);
            case "date" -> new FieldShape(FormSpotKind.DATE, QuestionLimits.NONE);
            default -> new FieldShape(FormSpotKind.TEXT, QuestionLimits.text(maxLength(text), null));
        };
    }

    /**
     * @param properties the properties of a content control
     * @return what it takes
     */
    static FieldShape ofControl(Element properties) {
        if (first(properties, W14, "checkbox") != null) return new FieldShape(FormSpotKind.CHECK, QuestionLimits.NONE);
        if (first(properties, W, "date") != null) return new FieldShape(FormSpotKind.DATE, QuestionLimits.NONE);
        var list = first(properties, W, "dropDownList");
        if (list == null) list = first(properties, W, "comboBox");
        if (list == null) return TEXT;
        return choice(children(list, W, "listItem").stream()
                .filter(item -> !attribute(item, "value").isBlank())
                .map(item -> attribute(item, "displayText").isBlank()
                        ? attribute(item, "value")
                        : attribute(item, "displayText"))
                .toList());
    }

    private static FieldShape choice(List<String> entries) {
        var kept = new LinkedHashMap<String, String>();
        for (var entry : entries) {
            String choice = entry.strip();
            if (choice.length() > QuestionLimits.MAX_CHOICE) {
                choice = choice.substring(0, QuestionLimits.MAX_CHOICE).strip();
            }
            if (!choice.isEmpty()) kept.putIfAbsent(choice.toLowerCase(Locale.ROOT), choice);
        }
        if (kept.isEmpty()) return TEXT;
        var choices = kept.values().stream().limit(QuestionLimits.MAX_CHOICES).toList();
        return new FieldShape(FormSpotKind.CHOICE, QuestionLimits.choice(choices));
    }

    private static @Nullable Integer maxLength(@Nullable Element text) {
        try {
            int length = Integer.parseInt(attribute(first(text, W, "maxLength"), "val"));
            return length >= 1 && length <= QuestionLimits.MAX_LENGTH ? length : null;
        } catch (NumberFormatException none) {
            return null;
        }
    }
}
