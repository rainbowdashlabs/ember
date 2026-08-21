/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.content.service;

import dev.chojo.ember.feature.content.entity.CellConfig;
import dev.chojo.ember.feature.content.entity.CellContentType;
import dev.chojo.ember.feature.content.entity.ContentCell;
import dev.chojo.ember.feature.content.entity.ContentRow;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Blocks written out as markdown.
 *
 * <p>This is the spine of the whole rich-article idea. A rich article stores both: the block tree,
 * which is the editable truth, and a markdown projection of it, which lands in the exact column a
 * simple article already uses. Every consumer downstream therefore keeps working with no change at
 * all - the search summary, the notification preview, the blog feed, the federation payload, the
 * full-text index, the export, the version history. Only rendering learns anything new.
 *
 * <p>The projection is derived data. Nothing edits it and nothing but the save path writes it,
 * which is exactly why the mode switch only goes one way.
 *
 * <p>Columns flatten to reading order: left to right, then down. That is the correct behaviour for
 * a screen reader, a search index and a printed document alike.
 */
public final class ContentProjection {

    private ContentProjection() {}

    /**
     * The whole container as markdown, blocks separated by a blank line.
     *
     * @param fileUrl how a media file is addressed from wherever the result will be read; the
     *                caller knows whether that is the public route or the authenticated one
     */
    public static String toMarkdown(List<ContentRow> rows, Function<String, String> fileUrl) {
        var blocks = new ArrayList<String>();
        for (var row : rows) {
            for (var cell : row.cells()) {
                String block = cellToMarkdown(cell, fileUrl);
                if (!block.isBlank()) blocks.add(block.strip());
            }
        }
        return String.join("\n\n", blocks);
    }

    /**
     * The same content with the markup taken off, for the places that want words rather than
     * formatting: a search index, a summary, a preview line.
     */
    public static String toPlainText(List<ContentRow> rows, Function<String, String> fileUrl) {
        return stripMarkup(toMarkdown(rows, fileUrl));
    }

    /**
     * Removes the markdown syntax and leaves the words. Deliberately blunt: this feeds a search
     * index and a preview, both of which care about what was written rather than how.
     */
    public static String stripMarkup(String markdown) {
        if (markdown == null || markdown.isBlank()) return "";
        return markdown.replaceAll("!\\[[^\\]]*]\\([^)]*\\)", "")
                .replaceAll("\\[([^\\]]*)]\\([^)]*\\)", "$1")
                .replaceAll("(?m)^\\s{0,3}#{1,6}\\s*", "")
                .replaceAll("(?m)^\\s{0,3}>\\s?", "")
                .replaceAll("(?m)^\\s{0,3}[-*+]\\s+", "")
                .replaceAll("(?m)^\\s{0,3}```.*$", "")
                .replaceAll("(?m)^\\s*---\\s*$", "")
                .replace("**", "")
                .replace("__", "")
                .replaceAll("[ \\t]+", " ")
                .replaceAll("\n{3,}", "\n\n")
                .strip();
    }

    private static String cellToMarkdown(ContentCell cell, Function<String, String> fileUrl) {
        String content = cell.content() == null ? "" : cell.content();
        var config = cell.config();
        return switch (cell.contentType()) {
            case MARKDOWN, EMPTY -> content;
            case IMAGE -> image(content, config, fileUrl);
            case IMAGE_GALLERY -> gallery(config, fileUrl);
            case HERO_BANNER -> heroBanner(config, fileUrl);
            case PAST_EVENT_RECAP -> pastEventRecap(config, fileUrl);
            case CALLOUT -> callout(content, config);
            case QUOTE -> quote(content, config);
            case CODE_BLOCK -> codeBlock(content, config);
            case ACCORDION -> section(config instanceof CellConfig.AccordionConfig a ? a.title() : null, content);
            case TABS -> tabs(config);
            case FILE_DOWNLOAD -> fileDownload(config);
            case PAGE_LINK ->
                linkLine(config instanceof CellConfig.PageLinkConfig p ? p.fallbackTitle() : null, content);
            case KB_ARTICLE ->
                linkLine(config instanceof CellConfig.KbArticleConfig k ? k.fallbackTitle() : null, content);
            case NEWS_TEASER -> newsTeaser(config);
            case EXTERNAL_LINK_CARD -> externalLinkCard(config);
            case VIDEO -> linkLine(null, content);
            case AUDIO_EMBED -> audio(config);
            case PDF -> pdf(config);
            case MAP -> map(config);
            case ADDRESS_CARD -> addressCard(config);
            case STATS_COUNTER -> statsCounter(config);
            case COUNTDOWN -> countdown(config);
            case DIVIDER -> "---";
            case SPACER -> "";
            case NESTED_ROWS -> nestedRows(config, fileUrl);
            default -> "";
        };
    }

    private static String image(String hash, CellConfig config, Function<String, String> fileUrl) {
        if (hash == null || hash.isBlank()) return "";
        String alt = "";
        String caption = "";
        if (config instanceof CellConfig.ImageConfig image) {
            alt = orEmpty(image.altText());
            caption = orEmpty(image.description());
        }
        String markdown = "![" + alt + "](" + fileUrl.apply(hash.trim()) + ")";
        return caption.isBlank() ? markdown : markdown + "\n\n" + caption;
    }

    private static String gallery(CellConfig config, Function<String, String> fileUrl) {
        if (!(config instanceof CellConfig.ImageGalleryConfig gallery) || gallery.items() == null) return "";
        var out = new ArrayList<String>();
        for (var item : gallery.items()) {
            if (item.imageHash() == null || item.imageHash().isBlank()) continue;
            String line = "![" + orEmpty(item.altText()) + "](" + fileUrl.apply(item.imageHash()) + ")";
            if (!orEmpty(item.subtext()).isBlank()) line = line + "\n\n" + item.subtext();
            out.add(line);
        }
        return String.join("\n\n", out);
    }

    private static String heroBanner(CellConfig config, Function<String, String> fileUrl) {
        if (!(config instanceof CellConfig.HeroBannerConfig hero)) return "";
        var out = new ArrayList<String>();
        if (hero.imageHash() != null && !hero.imageHash().isBlank()) {
            out.add("![" + orEmpty(hero.headline()) + "](" + fileUrl.apply(hero.imageHash()) + ")");
        }
        if (!orEmpty(hero.headline()).isBlank()) out.add("## " + hero.headline());
        if (!orEmpty(hero.subtitle()).isBlank()) out.add(hero.subtitle());
        if (!orEmpty(hero.ctaText()).isBlank() && !orEmpty(hero.ctaUrl()).isBlank()) {
            out.add("[" + hero.ctaText() + "](" + hero.ctaUrl() + ")");
        }
        return String.join("\n\n", out);
    }

    private static String pastEventRecap(CellConfig config, Function<String, String> fileUrl) {
        if (!(config instanceof CellConfig.PastEventRecapConfig recap)) return "";
        var out = new ArrayList<String>();
        if (recap.imageHash() != null && !recap.imageHash().isBlank()) {
            out.add("![" + orEmpty(recap.title()) + "](" + fileUrl.apply(recap.imageHash()) + ")");
        }
        if (!orEmpty(recap.title()).isBlank()) out.add("### " + recap.title());
        if (!orEmpty(recap.date()).isBlank()) out.add(recap.date());
        if (!orEmpty(recap.summary()).isBlank()) out.add(recap.summary());
        return String.join("\n\n", out);
    }

    private static String callout(String content, CellConfig config) {
        String lead = "";
        if (config instanceof CellConfig.CalloutConfig callout) {
            if (callout.title() != null && !callout.title().isBlank()) {
                lead = callout.title();
            } else if (callout.variant() != null) {
                lead = callout.variant().name();
            }
        }
        return blockquote(lead.isBlank() ? content : "**" + lead + "**\n\n" + content);
    }

    private static String quote(String content, CellConfig config) {
        String body = content;
        if (config instanceof CellConfig.QuoteConfig quote
                && !orEmpty(quote.author()).isBlank()) {
            body = body + "\n\n- " + quote.author();
        }
        return blockquote(body);
    }

    private static String blockquote(String body) {
        if (body == null || body.isBlank()) return "";
        var out = new StringBuilder();
        for (String line : body.strip().split("\n", -1)) {
            out.append("> ").append(line).append("\n");
        }
        return out.toString().stripTrailing();
    }

    private static String codeBlock(String content, CellConfig config) {
        if (content == null || content.isBlank()) return "";
        String language = config instanceof CellConfig.CodeBlockConfig code ? orEmpty(code.language()) : "";
        return "```" + language + "\n" + content.stripTrailing() + "\n```";
    }

    private static String tabs(CellConfig config) {
        if (!(config instanceof CellConfig.TabsConfig tabs) || tabs.items() == null) return "";
        var out = new ArrayList<String>();
        for (var item : tabs.items()) {
            out.add(section(item.title(), item.body()));
        }
        return String.join("\n\n", out.stream().filter(s -> !s.isBlank()).toList());
    }

    /**
     * A titled part of an accordion or a tab strip. Both are a heading with a body once the
     * folding is taken away, which is all a reader of the projection can be given.
     */
    private static String section(String title, String body) {
        var out = new ArrayList<String>();
        if (!orEmpty(title).isBlank()) out.add("### " + title);
        if (!orEmpty(body).isBlank()) out.add(body);
        return String.join("\n\n", out);
    }

    private static String fileDownload(CellConfig config) {
        if (!(config instanceof CellConfig.FileDownloadConfig file)) return "";
        String label = orEmpty(file.label()).isBlank() ? orEmpty(file.url()) : file.label();
        String line = linkLine(label, file.url());
        if (line.isBlank() || orEmpty(file.description()).isBlank()) return line;
        return line + "\n\n" + file.description();
    }

    private static String newsTeaser(CellConfig config) {
        if (!(config instanceof CellConfig.NewsTeaserConfig teaser)) return "";
        var out = new ArrayList<String>();
        String line = linkLine(teaser.title(), teaser.url());
        if (!line.isBlank()) out.add(line);
        if (!orEmpty(teaser.summary()).isBlank()) out.add(teaser.summary());
        return String.join("\n\n", out);
    }

    private static String externalLinkCard(CellConfig config) {
        if (!(config instanceof CellConfig.ExternalLinkCardConfig card)) return "";
        var out = new ArrayList<String>();
        String line = linkLine(card.title(), card.url());
        if (!line.isBlank()) out.add(line);
        if (!orEmpty(card.description()).isBlank()) out.add(card.description());
        return String.join("\n\n", out);
    }

    private static String audio(CellConfig config) {
        if (!(config instanceof CellConfig.AudioEmbedConfig audio)) return "";
        return linkLine(audio.title(), audio.url());
    }

    private static String pdf(CellConfig config) {
        if (!(config instanceof CellConfig.PdfConfig pdf)) return "";
        return linkLine(null, pdf.url());
    }

    private static String map(CellConfig config) {
        if (!(config instanceof CellConfig.MapConfig map)) return "";
        if (map.latitude() == null || map.longitude() == null) return orEmpty(map.label());
        String url = "https://www.openstreetmap.org/?mlat=" + map.latitude() + "&mlon=" + map.longitude();
        return linkLine(orEmpty(map.label()).isBlank() ? url : map.label(), url);
    }

    private static String addressCard(CellConfig config) {
        if (!(config instanceof CellConfig.AddressCardConfig address)) return "";
        var out = new ArrayList<String>();
        if (!orEmpty(address.label()).isBlank()) out.add(address.label());
        if (!orEmpty(address.addressLine()).isBlank()) out.add(address.addressLine());
        String city = (orEmpty(address.postalCode()) + " " + orEmpty(address.city())).strip();
        if (!city.isBlank()) out.add(city);
        if (!orEmpty(address.country()).isBlank()) out.add(address.country());
        return String.join("\n", out);
    }

    private static String statsCounter(CellConfig config) {
        if (!(config instanceof CellConfig.StatsCounterConfig stats) || stats.items() == null) return "";
        var out = new ArrayList<String>();
        for (var item : stats.items()) {
            String value = (orEmpty(item.value()) + orEmpty(item.suffix())).strip();
            out.add((orEmpty(item.label()) + " " + value).strip());
        }
        return String.join("\n", out.stream().filter(s -> !s.isBlank()).toList());
    }

    private static String countdown(CellConfig config) {
        if (!(config instanceof CellConfig.CountdownConfig countdown)) return "";
        var out = new ArrayList<String>();
        if (!orEmpty(countdown.label()).isBlank()) out.add(countdown.label());
        if (!orEmpty(countdown.sublabel()).isBlank()) out.add(countdown.sublabel());
        if (!orEmpty(countdown.targetDate()).isBlank()) out.add(countdown.targetDate());
        return String.join("\n", out);
    }

    /**
     * Nested rows carry their cells inside the config, so the projection recurses into them the
     * same way the reader's eye would: left to right, then down.
     */
    private static String nestedRows(CellConfig config, Function<String, String> fileUrl) {
        if (!(config instanceof CellConfig.NestedRowsConfig nested) || nested.rows() == null) return "";
        var blocks = new ArrayList<String>();
        for (JsonNode row : nested.rows()) {
            var cells = row.path("cells");
            if (!cells.isArray()) continue;
            for (JsonNode cell : cells) {
                var typeNode = cell.path("contentType");
                if (!typeNode.isString()) continue;
                CellContentType type;
                try {
                    type = CellContentType.valueOf(typeNode.asString());
                } catch (IllegalArgumentException ignored) {
                    continue;
                }
                var parsed = new ContentCell(
                        0,
                        0,
                        0,
                        100.0,
                        type,
                        cell.path("content").isString() ? cell.path("content").asString() : "",
                        CellConfig.parse(type, cell.get("config")));
                String block = cellToMarkdown(parsed, fileUrl);
                if (!block.isBlank()) blocks.add(block.strip());
            }
        }
        return String.join("\n\n", blocks);
    }

    private static String linkLine(String label, String url) {
        if (url == null || url.isBlank()) return orEmpty(label);
        String text = orEmpty(label).isBlank() ? url : label;
        return "[" + text + "](" + url + ")";
    }

    private static String orEmpty(String value) {
        return value == null ? "" : value;
    }
}
