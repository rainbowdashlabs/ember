/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.service;

import dev.chojo.ember.api.refusal.Refusal;
import dev.chojo.ember.feature.content.entity.CellConfig;
import dev.chojo.ember.feature.content.entity.ContentCell;
import dev.chojo.ember.feature.content.entity.ContentRow;
import dev.chojo.ember.feature.content.entity.ContentRows;
import dev.chojo.ember.feature.generator.entity.LetterContent;
import dev.chojo.ember.feature.generator.entity.MemberView;
import dev.chojo.ember.feature.generator.entity.SignatureRole;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Walks the rows of a letter for one member and lays them out as {@code letter.typ} draws them.
 *
 * <p>A row becomes a grid with one column per cell, each as wide as its share of the row, with a line
 * in every gap between two columns where the row asks for one. A cell is a text, a picture, a line, a
 * gap, a signature line, or rows stacked in it, which are walked the same way. A block the member does
 * not see ({@link MemberView#sees}) is left out, and so is everything stacked inside it; its column
 * stays, empty, so the others keep their place. A row left with nothing to print is dropped.
 *
 * <p>A signature block holds a field for each person its signer asks to sign for the member
 * ({@link SignatureRole#fieldNames}): one field mostly, one per guardian for every guardian, and none
 * for a second guardian the member does not have, which leaves the block out.
 *
 * <p>The texts are numbered in one fixed order, the header first, then the footer, then the body, each
 * top to bottom and left to right, and every text counts whether it is printed or not, the short text
 * under a signature line included. That is what lets the texts be converted once per state of the
 * template and found again by their number for every member, whatever each member sees of them.
 */
final class LetterLayout {
    /** Millimetres per CSS pixel, which is what the editor states a picture's height and a gap in. */
    static final double MM_PER_PIXEL = 25.4 / 96;

    /** The height of a gap whose block names none, as the editor shows it. */
    private static final int DEFAULT_SPACER_PX = 32;

    /**
     * What the walk makes of the blocks it prints. A walk that only looks at what is printed, rather than
     * drawing it, keeps the empty drawings these answer with.
     */
    interface Blocks {
        /**
         * @param index the number of the text in the letter
         * @param cell  the text block
         * @return how the text is drawn, or null where there is nothing to draw
         */
        default @Nullable Map<String, Object> text(int index, ContentCell cell) {
            return Map.of();
        }

        /**
         * @param cell the picture block
         * @return how the picture is drawn, or null where it cannot be read
         */
        default @Nullable Map<String, Object> image(ContentCell cell) {
            return Map.of();
        }

        /**
         * @param index  the number of the text under the line in the letter
         * @param cell   the signature block
         * @param fields the names of the signature fields on it, at least one
         * @return how the signature line is drawn
         */
        default Map<String, Object> signature(int index, ContentCell cell, List<String> fields) {
            return Map.of();
        }
    }

    private final MemberView view;
    private final Blocks blocks;
    private final List<String> signatureFields = new ArrayList<>();
    private int nextText;

    /**
     * @param view   what of the letter the member sees
     * @param blocks what to make of the blocks that are printed
     */
    LetterLayout(MemberView view, Blocks blocks) {
        this.view = view;
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
     * @param view   what of the letter the member sees
     * @return the texts, in the order they are printed
     */
    static List<String> visibleTexts(LetterContent letter, MemberView view) {
        var texts = new ArrayList<String>();
        new LetterLayout(view, new Blocks() {
                    @Override
                    public Map<String, Object> text(int index, ContentCell cell) {
                        texts.add(cell.content());
                        return Map.of();
                    }

                    @Override
                    public Map<String, Object> signature(int index, ContentCell cell, List<String> fields) {
                        texts.add(cell.content());
                        return Map.of();
                    }
                })
                .letter(letter);
        return texts;
    }

    /**
     * Refuses a letter that asks one person to sign in two fields of a member's document.
     *
     * @param letter  the letter
     * @param view    what of the letter the member sees
     * @param refusal what to refuse with
     */
    static void requireSignersOnce(LetterContent letter, MemberView view, Refusal refusal) {
        if (!SignatureRole.distinct(signatureFields(letter, view))) throw refusal.raise();
    }

    /**
     * The signature fields a member's letter carries.
     *
     * @param letter the letter
     * @param view   what of the letter the member sees
     * @return the names of the fields, in the order they are printed, a name twice where two lines ask
     *         for it
     */
    static List<String> signatureFields(LetterContent letter, MemberView view) {
        var layout = new LetterLayout(view, new Blocks() {});
        layout.letter(letter);
        return List.copyOf(layout.signatureFields);
    }

    /**
     * What the signer of each signature field of a member's letter confirms, where its line says so.
     *
     * @param letter the letter
     * @param view   what of the letter the member sees
     * @return the statement by the name of the field, without the fields that keep the default statement
     */
    static Map<String, String> signatureStatements(LetterContent letter, MemberView view) {
        var statements = new LinkedHashMap<String, String>();
        new LetterLayout(view, new Blocks() {
                    @Override
                    public Map<String, Object> signature(int index, ContentCell cell, List<String> fields) {
                        String statement = cell.config() instanceof CellConfig.SignatureConfig signature
                                ? signature.statement()
                                : null;
                        if (statement != null && !statement.isBlank()) {
                            fields.forEach(field -> statements.putIfAbsent(field, statement.strip()));
                        }
                        return Map.of();
                    }
                })
                .letter(letter);
        return statements;
    }

    private List<Map<String, Object>> rows(List<ContentRow> rows, boolean visible) {
        var out = new ArrayList<Map<String, Object>>();
        for (var row : rows) {
            var cells = new ArrayList<Map<String, Object>>();
            boolean printed = false;
            for (var cell : row.cells()) {
                var drawn = cell(cell, visible && view.sees(cell));
                printed |= drawn != null;
                cells.add(sized(drawn == null ? Map.of("kind", "empty") : drawn, cell.widthPercent()));
            }
            if (printed) out.add(Map.of("cells", cells, "lines", row.columnLines()));
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
            case DIVIDER -> visible ? divider(cell) : null;
            case SPACER -> visible ? spacer(cell) : null;
            case SIGNATURE -> {
                int index = nextText++;
                yield visible ? signature(index, cell) : null;
            }
            case NESTED_ROWS -> {
                var nested = cell.config() instanceof CellConfig.NestedRowsConfig config
                        ? rows(ContentRows.read(config.rows()), visible)
                        : List.<Map<String, Object>>of();
                yield nested.isEmpty() ? null : Map.of("kind", "rows", "rows", nested);
            }
            default -> null;
        };
    }

    private static Map<String, Object> divider(ContentCell cell) {
        String label = cell.config() instanceof CellConfig.DividerConfig divider ? divider.label() : null;
        return Map.of(
                "kind",
                "divider",
                "label",
                Objects.requireNonNullElse(label, "").strip());
    }

    private static Map<String, Object> spacer(ContentCell cell) {
        Integer height = cell.config() instanceof CellConfig.SpacerConfig spacer ? spacer.heightPx() : null;
        int pixels = height == null || height <= 0 ? DEFAULT_SPACER_PX : height;
        return Map.of("kind", "spacer", "heightMm", pixels * MM_PER_PIXEL);
    }

    private @Nullable Map<String, Object> signature(int index, ContentCell cell) {
        var signer = cell.config() instanceof CellConfig.SignatureConfig signature ? signature.signer() : null;
        if (signer == null) return null;
        var fields = signer.fieldNames(view.guardians());
        if (fields.isEmpty()) return null;
        signatureFields.addAll(fields);
        return blocks.signature(index, cell, fields);
    }

    private static Map<String, Object> sized(Map<String, Object> drawn, double widthPercent) {
        var out = new LinkedHashMap<>(drawn);
        out.put("width", widthPercent / 100.0);
        return out;
    }
}
