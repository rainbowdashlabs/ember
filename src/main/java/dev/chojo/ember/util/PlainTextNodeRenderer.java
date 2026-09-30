/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.util;

import org.commonmark.ext.gfm.strikethrough.Strikethrough;
import org.commonmark.ext.gfm.tables.TableCell;
import org.commonmark.node.BlockQuote;
import org.commonmark.node.Code;
import org.commonmark.node.HtmlBlock;
import org.commonmark.node.HtmlInline;
import org.commonmark.node.Image;
import org.commonmark.node.Link;
import org.commonmark.node.ListItem;
import org.commonmark.node.Node;
import org.commonmark.node.OrderedList;
import org.commonmark.node.ThematicBreak;
import org.commonmark.renderer.NodeRenderer;
import org.commonmark.renderer.text.TextContentNodeRendererContext;
import org.commonmark.renderer.text.TextContentWriter;
import org.jsoup.Jsoup;

import java.util.Set;

/**
 * The nodes {@link Markdown#toPlainText(String)} renders as words only, where CommonMark's text renderer
 * would add the link address, guillemets or quotes: images, inline HTML and rules vanish, block HTML keeps
 * its text, bullets become one glyph and table cells are joined with a middle dot.
 */
final class PlainTextNodeRenderer implements NodeRenderer {

    private static final String CELL_SEPARATOR = " · ";
    private static final String BULLET = "•";

    private final TextContentNodeRendererContext context;
    private final TextContentWriter writer;

    PlainTextNodeRenderer(TextContentNodeRendererContext context) {
        this.context = context;
        this.writer = context.getWriter();
    }

    @Override
    public Set<Class<? extends Node>> getNodeTypes() {
        return Set.of(
                Link.class,
                Image.class,
                Code.class,
                BlockQuote.class,
                ThematicBreak.class,
                HtmlInline.class,
                HtmlBlock.class,
                TableCell.class,
                Strikethrough.class,
                ListItem.class);
    }

    @Override
    public void render(Node node) {
        switch (node) {
            case Link link -> renderChildren(link);
            case Strikethrough strikethrough -> renderChildren(strikethrough);
            case Code code -> writer.write(code.getLiteral());
            case BlockQuote quote -> {
                renderChildren(quote);
                writer.block();
            }
            case HtmlBlock html -> {
                writer.write(Jsoup.parse(html.getLiteral()).text());
                writer.block();
            }
            case TableCell cell -> {
                renderChildren(cell);
                if (cell.getNext() != null) writer.write(CELL_SEPARATOR);
            }
            case ListItem item -> renderListItem(item);
            case ThematicBreak _ -> writer.block();
            default -> {}
        }
    }

    private void renderListItem(ListItem item) {
        String marker = item.getParent() instanceof OrderedList ordered ? orderedMarker(ordered, item) : BULLET;
        writer.write(marker + " ");
        writer.pushPrefix(" ".repeat(marker.length() + 1));
        renderChildren(item);
        writer.block();
        writer.popPrefix();
    }

    private static String orderedMarker(OrderedList list, ListItem item) {
        int number = list.getMarkerStartNumber() == null ? 1 : list.getMarkerStartNumber();
        for (Node sibling = item.getPrevious(); sibling != null; sibling = sibling.getPrevious()) {
            number++;
        }
        String delimiter = list.getMarkerDelimiter() == null ? "." : list.getMarkerDelimiter();
        return number + delimiter;
    }

    private void renderChildren(Node parent) {
        Node child = parent.getFirstChild();
        while (child != null) {
            Node next = child.getNext();
            context.render(child);
            child = next;
        }
    }
}
