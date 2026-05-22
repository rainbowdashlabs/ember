/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.service;

import org.commonmark.Extension;
import org.commonmark.ext.autolink.AutolinkExtension;
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.ext.heading.anchor.HeadingAnchorExtension;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests that CommonMark renders all supported markdown features to proper HTML.
 * Uses the same extensions as KnowledgeBaseService.
 */
class MarkdownRenderingTest {

    private static Parser parser;
    private static HtmlRenderer renderer;

    @BeforeAll
    static void setup() {
        List<Extension> extensions = List.of(
                TablesExtension.create(),
                HeadingAnchorExtension.create(),
                AutolinkExtension.create(),
                StrikethroughExtension.create());
        parser = Parser.builder().extensions(extensions).build();
        renderer = HtmlRenderer.builder().extensions(extensions).build();
    }

    private String render(String markdown) {
        return renderer.render(parser.parse(markdown));
    }

    @Test
    void headings() {
        assertTrue(render("# Heading 1").contains("<h1"));
        assertTrue(render("## Heading 2").contains("<h2"));
        assertTrue(render("### Heading 3").contains("<h3"));
    }

    @Test
    void bold() {
        String html = render("This is **bold** text");
        assertTrue(html.contains("<strong>bold</strong>"), "Expected <strong> tag, got: " + html);
    }

    @Test
    void italic() {
        String html = render("This is *italic* text");
        assertTrue(html.contains("<em>italic</em>"), "Expected <em> tag, got: " + html);
    }

    @Test
    void strikethrough() {
        String html = render("This is ~~deleted~~ text");
        assertTrue(html.contains("<del>deleted</del>"), "Expected <del> tag, got: " + html);
    }

    @Test
    void inlineCode() {
        String html = render("This is `code` text");
        assertTrue(html.contains("<code>code</code>"), "Expected <code> tag, got: " + html);
    }

    @Test
    void codeBlock() {
        String html = render("""
                ```
                function hello() {
                  return 42;
                }
                ```
                """);
        assertTrue(html.contains("<pre>"), "Expected <pre> tag, got: " + html);
        assertTrue(html.contains("<code>"), "Expected <code> inside pre, got: " + html);
        assertTrue(html.contains("function hello()"), "Expected code content, got: " + html);
    }

    @Test
    void unorderedList() {
        String html = render("""
                - Item 1
                - Item 2
                - Item 3
                """);
        assertTrue(html.contains("<ul>"), "Expected <ul> tag, got: " + html);
        assertTrue(html.contains("<li>"), "Expected <li> tags, got: " + html);
        assertTrue(html.contains("Item 1"), "Expected list content, got: " + html);
    }

    @Test
    void orderedList() {
        String html = render("""
                1. First
                2. Second
                3. Third
                """);
        assertTrue(html.contains("<ol>"), "Expected <ol> tag, got: " + html);
        assertTrue(html.contains("<li>"), "Expected <li> tags, got: " + html);
    }

    @Test
    void blockquote() {
        String html = render("> This is a quote");
        assertTrue(html.contains("<blockquote>"), "Expected <blockquote> tag, got: " + html);
        assertTrue(html.contains("This is a quote"), "Expected quote content, got: " + html);
    }

    @Test
    void link() {
        String html = render("[Click here](https://example.com)");
        assertTrue(html.contains("<a"), "Expected <a> tag, got: " + html);
        assertTrue(html.contains("href=\"https://example.com\""), "Expected href, got: " + html);
        assertTrue(html.contains("Click here"), "Expected link text, got: " + html);
    }

    @Test
    void horizontalRule() {
        String html = render("---");
        assertTrue(html.contains("<hr"), "Expected <hr> tag, got: " + html);
    }

    @Test
    void table() {
        String html = render("""
                | Header A | Header B |
                |----------|----------|
                | Cell 1   | Cell 2   |
                | Cell 3   | Cell 4   |
                """);
        assertTrue(html.contains("<table>"), "Expected <table> tag, got: " + html);
        assertTrue(html.contains("<thead>"), "Expected <thead> tag, got: " + html);
        assertTrue(html.contains("<th>"), "Expected <th> tags, got: " + html);
        assertTrue(html.contains("<tbody>"), "Expected <tbody> tag, got: " + html);
        assertTrue(html.contains("<td>"), "Expected <td> tags, got: " + html);
        assertTrue(html.contains("Header A"), "Expected header content, got: " + html);
        assertTrue(html.contains("Cell 1"), "Expected cell content, got: " + html);
    }

    @Test
    void image() {
        String html = render("![Alt text](image.png)");
        assertTrue(html.contains("<img"), "Expected <img> tag, got: " + html);
        assertTrue(html.contains("src=\"image.png\""), "Expected src attribute, got: " + html);
        assertTrue(html.contains("alt=\"Alt text\""), "Expected alt attribute, got: " + html);
    }

    @Test
    void externalImage() {
        String html = render("![Photo](https://i.imgur.com/abc123.png)");
        assertTrue(html.contains("<img"), "Expected <img> tag, got: " + html);
        assertTrue(html.contains("src=\"https://i.imgur.com/abc123.png\""), "Expected imgur src, got: " + html);
    }

    @Test
    void iframePassthrough() {
        String html = render("""
                <iframe src="https://www.youtube-nocookie.com/embed/abc123" width="560" height="315" frameborder="0" allowfullscreen></iframe>
                """);
        assertTrue(html.contains("<iframe"), "Expected iframe passthrough, got: " + html);
        assertTrue(html.contains("youtube-nocookie.com"), "Expected youtube URL in iframe, got: " + html);
    }

    @Test
    void peertubeIframePassthrough() {
        String html = render("""
                <iframe src="https://tube.example.org/videos/embed/abc-123" width="560" height="315" frameborder="0" allowfullscreen></iframe>
                """);
        assertTrue(html.contains("<iframe"), "Expected iframe passthrough, got: " + html);
        assertTrue(html.contains("tube.example.org"), "Expected peertube URL, got: " + html);
    }

    @Test
    void paragraph() {
        String html = render("Just a paragraph.");
        assertTrue(html.contains("<p>"), "Expected <p> tag, got: " + html);
    }

    @Test
    void htmlPassthrough() {
        // Underline and colored spans are HTML — CommonMark should pass them through
        String html = render("<u>underlined</u>");
        assertTrue(html.contains("<u>underlined</u>"), "Expected <u> passthrough, got: " + html);
    }

    @Test
    void coloredSpanPassthrough() {
        String html = render("<span style=\"color: red\">red text</span>");
        assertTrue(html.contains("style=\"color: red\""), "Expected style passthrough, got: " + html);
    }

    @Test
    void fullDocument() {
        String markdown = """
                # Formatierungsbeispiele

                Normaler Text mit **fett**, *kursiv* und ~~durchgestrichen~~.

                ## Listen

                - Punkt 1
                - Punkt 2
                - Punkt 3

                1. Erster
                2. Zweiter

                > Ein Zitat

                | A | B |
                |---|---|
                | 1 | 2 |

                ```
                code block
                ```

                ---

                [Link](https://example.com)
                """;
        String html = render(markdown);
        assertTrue(html.contains("<h1"), "Missing h1");
        assertTrue(html.contains("<h2"), "Missing h2");
        assertTrue(html.contains("<strong>"), "Missing bold");
        assertTrue(html.contains("<em>"), "Missing italic");
        assertTrue(html.contains("<del>"), "Missing strikethrough");
        assertTrue(html.contains("<ul>"), "Missing unordered list");
        assertTrue(html.contains("<ol>"), "Missing ordered list");
        assertTrue(html.contains("<blockquote>"), "Missing blockquote");
        assertTrue(html.contains("<table>"), "Missing table");
        assertTrue(html.contains("<pre>"), "Missing code block");
        assertTrue(html.contains("<hr"), "Missing horizontal rule");
        assertTrue(html.contains("<a"), "Missing link");
    }
}
