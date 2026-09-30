/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.util;

import org.commonmark.Extension;
import org.commonmark.ext.autolink.AutolinkExtension;
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.ext.heading.anchor.HeadingAnchorExtension;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.commonmark.renderer.text.LineBreakRendering;
import org.commonmark.renderer.text.TextContentRenderer;

import java.util.List;

/**
 * Markdown as this application renders it: tables, heading anchors, autolinks and strikethrough.
 *
 * <p>One place, because the wiki, the news, the pages and the legal documents all write the same
 * body. Rendering the same markdown two ways would eventually show a reader two different documents.
 * The HTML is sanitised before it ever reaches a browser, with the policy the caller names; the plain
 * text is the same document with the markup taken off, for a search index, a summary or a preview.
 */
public final class Markdown {

    private static final List<Extension> EXTENSIONS = List.of(
            TablesExtension.create(),
            HeadingAnchorExtension.create(),
            AutolinkExtension.create(),
            StrikethroughExtension.create());

    private static final Parser PARSER = Parser.builder().extensions(EXTENSIONS).build();

    private static final HtmlRenderer RENDERER =
            HtmlRenderer.builder().extensions(EXTENSIONS).sanitizeUrls(true).build();

    private static final TextContentRenderer TEXT_RENDERER = TextContentRenderer.builder()
            .nodeRendererFactory(PlainTextNodeRenderer::new)
            .extensions(EXTENSIONS)
            .lineBreakRendering(LineBreakRendering.SEPARATE_BLOCKS)
            .build();

    private Markdown() {}

    /**
     * Renders markdown to sanitised HTML. Blank input gives back an empty string rather than an
     * empty document, because a caller storing the result wants nothing rather than markup.
     *
     * @param markdown the source, may be {@code null}
     * @param policy   what the sanitiser lets through
     * @return the HTML, or an empty string for blank input
     */
    public static String toHtml(String markdown, HtmlSanitizer.Policy policy) {
        if (markdown == null || markdown.isBlank()) return "";
        return HtmlSanitizer.sanitize(RENDERER.render(PARSER.parse(markdown)), policy);
    }

    /**
     * The words of a markdown document without its markup: headings, emphasis, quotes and list
     * items keep their text, a link keeps its label, an image and embedded HTML tags disappear, and
     * the cells of a table are joined with a middle dot. Blocks are separated by a blank line.
     *
     * @param markdown the source, may be {@code null}
     * @return the plain text, or an empty string for blank input
     */
    public static String toPlainText(String markdown) {
        if (markdown == null || markdown.isBlank()) return "";
        return TEXT_RENDERER.render(PARSER.parse(markdown)).strip();
    }
}
