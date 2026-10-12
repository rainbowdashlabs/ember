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
    /** A tick, printed as a ticked or an empty box and ticking a check field or a check box. */
    CHECK
}
