/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.entity;

/**
 * What one cell of a letterhead holds.
 */
public enum LetterCellKind {
    /** Nothing, keeping its third of the row free. */
    EMPTY,
    /** A picture from the station's media library. */
    IMAGE,
    /** The station's logo, read when the document is generated. */
    LOGO,
    /** A few lines of text, which may hold placeholders. */
    TEXT
}
