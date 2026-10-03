/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.entity;

import dev.chojo.ember.feature.question.QuestionConfigs;

import java.util.List;
import java.util.stream.Stream;

/**
 * The header and the footer of a letter, each a row of up to three cells drawn on every page.
 *
 * @param header the cells across the top of the page, left to right
 * @param footer the cells across the bottom of the page, left to right
 */
public record Letterhead(List<LetterCell> header, List<LetterCell> footer) {
    /** How many cells a row holds at most. */
    public static final int MAX_CELLS = 3;

    private static final Letterhead EMPTY = new Letterhead(List.of(), List.of());

    public Letterhead {
        header = header == null ? List.of() : List.copyOf(header);
        footer = footer == null ? List.of() : List.copyOf(footer);
    }

    /** A letterhead with nothing in it. */
    public static Letterhead empty() {
        return EMPTY;
    }

    /**
     * @return every cell of the header and the footer
     */
    public Stream<LetterCell> cells() {
        return Stream.concat(header.stream(), footer.stream());
    }

    /**
     * Reads the stored column, answering an empty letterhead where it says nothing readable.
     *
     * @param json the column
     * @return the letterhead
     */
    public static Letterhead parse(String json) {
        return QuestionConfigs.parse(json, Letterhead.class, EMPTY);
    }

    /**
     * @return the letterhead as it is stored
     */
    public String toJson() {
        return QuestionConfigs.toJson(this);
    }
}
