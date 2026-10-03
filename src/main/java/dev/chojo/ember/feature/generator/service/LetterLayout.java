/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.service;

import dev.chojo.ember.feature.content.entity.CellConfig;
import dev.chojo.ember.feature.content.entity.ContentCell;
import dev.chojo.ember.feature.content.entity.ContentRow;
import dev.chojo.ember.feature.content.entity.ContentRows;
import dev.chojo.ember.feature.generator.entity.LetterContent;
import dev.chojo.ember.feature.restriction.RestrictionAudience;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Walks the rows of a letter for one member and lays them out as {@code letter.typ} draws them.
 *
 * <p>A row becomes a grid with one column per cell, each as wide as its share of the row. A cell is a
 * text, a picture, or rows stacked in it, which are walked the same way. A block whose restriction the
 * member does not match is left out, and so is everything stacked inside it; its column stays, empty,
 * so the others keep their place. A row left with nothing to print is dropped.
 *
 * <p>The texts are numbered in one fixed order, the header first, then the footer, then the body, each
 * top to bottom and left to right, and every text counts whether it is printed or not. That is what lets
 * the texts be converted once per state of the template and found again by their number for every
 * member, whatever each member sees of them.
 */
final class LetterLayout {
    /** Who sees everything: a preview without a member, and the conversion of the texts. */
    static final Predicate<RestrictionAudience> EVERYBODY = audience -> true;

    /** What the walk makes of the blocks it prints. */
    interface Blocks {
        /**
         * @param index the number of the text in the letter
         * @param cell  the text block
         * @return how the text is drawn, or null where there is nothing to draw
         */
        @Nullable
        Map<String, Object> text(int index, ContentCell cell);

        /**
         * @param cell the picture block
         * @return how the picture is drawn, or null where it cannot be read
         */
        @Nullable
        Map<String, Object> image(ContentCell cell);
    }

    private final Predicate<RestrictionAudience> shown;
    private final Blocks blocks;
    private int nextText;

    /**
     * @param shown  whether the member a letter is for belongs to a block's audience
     * @param blocks what to make of the blocks that are printed
     */
    LetterLayout(Predicate<RestrictionAudience> shown, Blocks blocks) {
        this.shown = shown;
        this.blocks = blocks;
    }

    /**
     * Lays out a whole letter.
     *
     * @param letter the letter
     * @return the header, the footer and the body, each as the rows {@code letter.typ} draws
     */
    Map<String, Object> letter(LetterContent letter) {
        var out = new LinkedHashMap<String, Object>();
        out.put("header", rows(letter.header(), true));
        out.put("footer", rows(letter.footer(), true));
        out.put("body", rows(letter.body(), true));
        return out;
    }

    /**
     * The texts of a letter a member sees, which are the ones whose placeholders need a value.
     *
     * @param letter the letter
     * @param shown  whether the member belongs to a block's audience
     * @return the texts, in the order they are printed
     */
    static List<String> visibleTexts(LetterContent letter, Predicate<RestrictionAudience> shown) {
        var texts = new ArrayList<String>();
        new LetterLayout(shown, new Blocks() {
                    @Override
                    public Map<String, Object> text(int index, ContentCell cell) {
                        texts.add(cell.content());
                        return Map.of();
                    }

                    @Override
                    public Map<String, Object> image(ContentCell cell) {
                        return Map.of();
                    }
                })
                .letter(letter);
        return texts;
    }

    private List<Map<String, Object>> rows(List<ContentRow> rows, boolean visible) {
        var out = new ArrayList<Map<String, Object>>();
        for (var row : rows) {
            var cells = new ArrayList<Map<String, Object>>();
            boolean printed = false;
            for (var cell : row.cells()) {
                var restriction = cell.restriction();
                boolean cellVisible = visible && (restriction == null || shown.test(restriction));
                var drawn = cell(cell, cellVisible);
                printed |= drawn != null;
                cells.add(sized(drawn == null ? Map.of("kind", "empty") : drawn, cell.widthPercent()));
            }
            if (printed) out.add(Map.of("cells", cells));
        }
        return out;
    }

    /**
     * The cell as it is drawn, or null where nothing of it is printed. Its texts are counted whether it
     * is printed or not.
     */
    private @Nullable Map<String, Object> cell(ContentCell cell, boolean visible) {
        return switch (cell.contentType()) {
            case MARKDOWN -> {
                int index = nextText++;
                yield visible && !cell.content().isBlank() ? blocks.text(index, cell) : null;
            }
            case IMAGE -> visible ? blocks.image(cell) : null;
            case NESTED_ROWS -> {
                var nested = cell.config() instanceof CellConfig.NestedRowsConfig config
                        ? rows(ContentRows.read(config.rows()), visible)
                        : List.<Map<String, Object>>of();
                yield nested.isEmpty() ? null : Map.of("kind", "rows", "rows", nested);
            }
            default -> null;
        };
    }

    private static Map<String, Object> sized(Map<String, Object> drawn, double widthPercent) {
        var out = new LinkedHashMap<>(drawn);
        out.put("width", widthPercent / 100.0);
        return out;
    }
}
