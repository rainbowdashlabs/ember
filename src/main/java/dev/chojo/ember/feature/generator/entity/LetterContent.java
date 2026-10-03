/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

import java.util.Objects;
import java.util.stream.Stream;

/**
 * What a letter template says and how it looks.
 *
 * @param letterhead   the header and the footer
 * @param bodyMarkdown the body as markdown, placeholders written as {@code {{key}}}
 * @param page         the margins and the size of the body text
 */
public record LetterContent(Letterhead letterhead, String bodyMarkdown, LetterPage page) implements TemplateContent {

    /** An empty letter on the default page. */
    public static LetterContent blank() {
        return new LetterContent(Letterhead.empty(), "", LetterPage.defaults());
    }

    @Override
    public DocumentTemplateKind kind() {
        return DocumentTemplateKind.LETTER;
    }

    @Override
    public Stream<String> texts() {
        return Stream.concat(
                Stream.of(bodyMarkdown),
                letterhead.cells().map(LetterCell::text).filter(Objects::nonNull));
    }

    public static RowMapping<LetterContent> map() {
        return row -> new LetterContent(
                Letterhead.parse(row.getString("letterhead")),
                row.getString("body_markdown"),
                LetterPage.parse(row.getString("page")));
    }
}
