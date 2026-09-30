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
 * The one markdown pipeline (tables, heading anchors, autolinks, strikethrough), so no two features show the
 * same body differently: sanitised HTML, or plain text for search, summaries and previews.
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

    /** Markdown as HTML sanitised with the policy; blank or {@code null} input gives an empty string. */
    public static String toHtml(String markdown, HtmlSanitizer.Policy policy) {
        if (markdown == null || markdown.isBlank()) return "";
        return HtmlSanitizer.sanitize(RENDERER.render(PARSER.parse(markdown)), policy);
    }

    /**
     * The words of a markdown document without markup, blocks separated by a blank line; blank or {@code null}
     * input gives an empty string.
     */
    public static String toPlainText(String markdown) {
        if (markdown == null || markdown.isBlank()) return "";
        return TEXT_RENDERER.render(PARSER.parse(markdown)).strip();
    }
}
