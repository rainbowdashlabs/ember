/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;
import dev.chojo.ember.feature.content.entity.CellConfig;
import dev.chojo.ember.feature.content.entity.CellContentType;
import dev.chojo.ember.feature.content.entity.ContentCell;
import dev.chojo.ember.feature.content.entity.ContentRow;
import dev.chojo.ember.feature.content.entity.ContentRows;

import java.util.List;
import java.util.stream.Stream;

/**
 * What a letter template says and how it looks: a header and a footer drawn on every page, the body,
 * and the page they are set on. All three are rows of blocks, the same rows pages are built from: a row
 * holds up to three columns, a column a text, a picture, a line, a gap, or blocks stacked in it, and the
 * body also signature lines.
 *
 * @param header the rows across the top of every page
 * @param footer the rows across the bottom of every page
 * @param body   the rows of the letter itself
 * @param page   the margins and the size of the body text
 */
public record LetterContent(List<ContentRow> header, List<ContentRow> footer, List<ContentRow> body, LetterPage page)
        implements TemplateContent {

    public LetterContent {
        header = List.copyOf(header);
        footer = List.copyOf(footer);
        body = List.copyOf(body);
    }

    /** An empty letter on the default page. */
    public static LetterContent blank() {
        return new LetterContent(List.of(), List.of(), List.of(), LetterPage.defaults());
    }

    @Override
    public DocumentTemplateKind kind() {
        return DocumentTemplateKind.LETTER;
    }

    @Override
    public Stream<String> texts() {
        return Stream.of(header, footer, body).flatMap(LetterContent::textsOf);
    }

    /** The families of the page, then those its texts set words in ({@link FontSpans}). */
    @Override
    public Stream<String> fontFamilies() {
        return Stream.concat(page.fontFamilies(), texts().flatMap(FontSpans::familiesIn));
    }

    /**
     * Every block of some rows that is no stack of others, the blocks stacked in a column included.
     *
     * @param rows the rows
     * @return the blocks, top to bottom and left to right
     */
    public static Stream<ContentCell> blocks(List<ContentRow> rows) {
        return rows.stream().flatMap(row -> row.cells().stream()).flatMap(cell -> {
            if (cell.config() instanceof CellConfig.NestedRowsConfig nested) {
                return blocks(ContentRows.read(nested.rows()));
            }
            return Stream.of(cell);
        });
    }

    /**
     * @param rows some rows
     * @return the text of every text block in them and the short text under every signature line
     */
    public static Stream<String> textsOf(List<ContentRow> rows) {
        return blocks(rows)
                .filter(cell -> cell.contentType() == CellContentType.MARKDOWN
                        || cell.contentType() == CellContentType.SIGNATURE)
                .map(ContentCell::content);
    }

    public static RowMapping<LetterContent> map() {
        return row -> new LetterContent(
                ContentRows.read(row.getString("header")),
                ContentRows.read(row.getString("footer")),
                ContentRows.read(row.getString("body")),
                LetterPage.parse(row.getString("page")));
    }
}
