/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.service.wordform;

import org.jspecify.annotations.Nullable;

/**
 * A place in a Word form that is filled in: a text field, a date field or a check box.
 *
 * @param number the place counted from one in the order the document holds them
 * @param kind   what is filled in
 * @param label  the words the form shows it by, read from the text around it; empty where it shows none
 * @param preset what the form holds in it already, or null where it holds nothing
 */
public record FormSpot(
        int number,
        FormSpotKind kind,
        String label,
        @Nullable String preset) {

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
        /** A tick. */
        CHECK
    }
}
