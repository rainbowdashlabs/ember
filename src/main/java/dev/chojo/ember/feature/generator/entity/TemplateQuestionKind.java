/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.entity;

/**
 * What a question of a template takes from the person generating a document.
 */
public enum TemplateQuestionKind {
    /** A text, printed as it was written. */
    TEXT,
    /** A day, printed like every other date and taking the same formats. */
    DATE,
    /** A number, kept as a plain decimal and printed in the template's language. */
    NUMBER,
    /** One of the entries the question offers, printed as it is written. */
    CHOICE,
    /** A tick, printed as a ticked or an empty box and ticking a check field or a check box. */
    CHECK
}
