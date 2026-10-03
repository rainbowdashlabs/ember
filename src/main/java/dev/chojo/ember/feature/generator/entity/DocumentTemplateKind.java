/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.entity;

/**
 * What a document template is made of.
 */
public enum DocumentTemplateKind {
    /** A letterhead and a body written in Ember, rendered through Typst. */
    LETTER,
    /** An uploaded PDF, filled in place with fields drawn on its pages and with its own form fields. */
    PDF
}
