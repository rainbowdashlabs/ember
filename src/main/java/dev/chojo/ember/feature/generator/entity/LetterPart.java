/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.entity;

/**
 * The parts of a letter, which differ in how long a text may be, how many columns a row holds and
 * whether a signature line stands in them.
 */
public enum LetterPart {
    /**
     * The header and the footer, printed on every page: short texts, up to four columns for the rare
     * letterhead that needs them, and no signature line.
     */
    LETTERHEAD(600, 4, false),
    /** The body: texts long enough for a letter of several pages, up to three columns, and signature lines. */
    BODY(200_000, 3, true);

    private final int maxText;
    private final int maxColumns;
    private final boolean signatures;

    LetterPart(int maxText, int maxColumns, boolean signatures) {
        this.maxText = maxText;
        this.maxColumns = maxColumns;
        this.signatures = signatures;
    }

    /** @return the longest text of a block */
    public int maxText() {
        return maxText;
    }

    /** @return the most columns a row holds, at any depth */
    public int maxColumns() {
        return maxColumns;
    }

    /** @return whether signature lines stand here */
    public boolean signatures() {
        return signatures;
    }
}
