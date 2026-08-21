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

import java.util.List;

/**
 * Markdown as this application renders it: tables, heading anchors, autolinks and strikethrough,
 * with the result sanitised before it ever reaches a browser.
 *
 * <p>One place, because three features write the same body and a fourth is about to. Rendering the
 * same markdown two ways would eventually show a reader two different documents.
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

    private Markdown() {}

    /**
     * Renders markdown to sanitised HTML. Blank input gives back an empty string rather than an
     * empty document, because a caller storing the result wants nothing rather than markup.
     */
    public static String toHtml(String markdown) {
        if (markdown == null || markdown.isBlank()) return "";
        return HtmlSanitizer.sanitize(RENDERER.render(PARSER.parse(markdown)), HtmlSanitizer.Policy.RICH);
    }
}
