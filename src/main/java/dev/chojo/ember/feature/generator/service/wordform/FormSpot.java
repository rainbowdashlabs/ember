/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.service.wordform;

import dev.chojo.ember.feature.generator.entity.QuestionLimits;
import org.jspecify.annotations.Nullable;

/**
 * A place in a Word form that is filled in: a text field, a number field, a date field, a list or a
 * check box.
 *
 * @param number the place counted from one in the order the document holds them
 * @param kind   what is filled in
 * @param label  the words the form shows it by, read from the text around it; empty where it shows none
 * @param preset what the form holds in it already, or null where it holds nothing
 * @param limits what the form lets be filled in: the longest text, the entries of a list
 */
public record FormSpot(
        int number,
        FormSpotKind kind,
        String label,
        @Nullable String preset,
        QuestionLimits limits) {

    /**
     * A place without limits.
     *
     * @param number the place counted from one in the order the document holds them
     * @param kind   what is filled in
     * @param label  the words the form shows it by
     * @param preset what the form holds in it already, or null
     */
    public FormSpot(int number, FormSpotKind kind, String label, @Nullable String preset) {
        this(number, kind, label, preset, QuestionLimits.NONE);
    }

    /** What the place is called in the converted PDF, where its form field carries this as its name. */
    public String tag() {
        return "ember-f" + number;
    }

    /** What is filled in at a place. */
    public enum FormSpotKind {
        /** A text. */
        TEXT,
        /** A day. */
        DATE,
        /** A number. */
        NUMBER,
        /** An entry of a list. */
        CHOICE,
        /** A tick. */
        CHECK
    }
}
