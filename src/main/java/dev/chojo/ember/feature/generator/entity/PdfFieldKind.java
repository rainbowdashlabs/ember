/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.entity;

/**
 * What a field drawn on the page of a PDF template does.
 */
public enum PdfFieldKind {
    /** Writes a text with placeholders into its box. */
    TEXT,
    /** Draws a cross into its box where its value says yes. */
    CHECK,
    /** Becomes an empty signature field for one signer. */
    SIGNATURE,
    /** Becomes an empty text field the signer of one signature field types into when they sign. */
    FILL_IN
}
