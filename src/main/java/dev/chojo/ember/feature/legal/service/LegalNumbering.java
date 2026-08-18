/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.legal.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Numbers the headings of a legal document and resolves the references that point at them.
 *
 * <p>A number written into the text goes stale the moment a section moves. Here the author writes
 * the heading without one - {@code ## Nutzungsregeln} - and refers to it by its anchor:
 * {@code {{ ref:nutzungsregeln }}} for the bare number, {@code {{ ref!nutzungsregeln }}} for the
 * number with the title behind it. Numbers are assigned while rendering, so reordering the sections
 * is enough to renumber the document and every reference in it.
 *
 * <p>An anchor is the slug of the heading, or whatever an explicit {@code {#anchor}} at the end of
 * the heading says. A reference whose target does not exist is never dropped silently: it renders
 * as a visible marker and is reported through {@link Result#unresolved()}, so the editor can name
 * it before the document is published.
 */
public final class LegalNumbering {

    /**
     * How the levels of a document are counted.
     */
    public enum Style {
        /**
         * Paragraph counting, as the terms of service use it: {@code § 12}, then {@code 12.3}.
         */
        PARAGRAPH,
        /**
         * Plain counting, as the privacy policy uses it: {@code 12.}, then {@code 12.3}.
         */
        DECIMAL,
        /**
         * No numbering at all. References then carry the heading title.
         */
        NONE
    }

    /**
     * The rendered markdown together with what the pass learned about it.
     *
     * @param markdown   the document with its numbers written in and its references resolved
     * @param anchors    the anchor of every heading in document order
     * @param unresolved the references that pointed at a heading that does not exist
     */
    public record Result(String markdown, List<String> anchors, Set<String> unresolved) {}

    private record Heading(String anchor, String number, String title) {}

    private static final Pattern HEADING = Pattern.compile("^(#{1,6})\\s+(.*?)\\s*$");
    private static final Pattern EXPLICIT_ANCHOR = Pattern.compile("\\s*\\{#([A-Za-z0-9_.\\-]+)}\\s*$");
    private static final Pattern REFERENCE = Pattern.compile("\\{\\{\\s*ref([:!])([A-Za-z0-9_.\\-]+)\\s*}}");
    /**
     * A number an author may have left in a heading. Import strips these; rendering ignores them so
     * a document that still carries them is not numbered twice.
     */
    private static final Pattern LEADING_NUMBER =
            Pattern.compile("^(?:§+\\s*|Art\\.?\\s+|Artikel\\s+|Section\\s+|Ziffer\\s+)?\\d+(?:\\.\\d+)*[.)]?\\s+");

    /**
     * The heading level the counting starts at. Level one is the document title and stays unnumbered.
     */
    private static final int TOP_LEVEL = 2;

    private LegalNumbering() {}

    /**
     * Numbers the headings of the given markdown and resolves its references.
     *
     * @param markdown the assembled document
     * @param style    how the levels are counted
     * @return the rewritten markdown, the anchors it defines and the references it could not resolve
     */
    public static Result apply(String markdown, Style style) {
        return apply(markdown, style, "§");
    }

    /**
     * Numbers the headings of the given markdown and resolves its references.
     *
     * @param markdown      the assembled document
     * @param style         how the levels are counted
     * @param paragraphSign what a top-level number is introduced with, for locales that do not
     *                      write {@code §}
     * @return the rewritten markdown, the anchors it defines and the references it could not resolve
     */
    public static Result apply(String markdown, Style style, String paragraphSign) {
        if (markdown == null || markdown.isEmpty()) {
            return new Result("", List.of(), Set.of());
        }

        Map<String, Heading> headings = new LinkedHashMap<>();
        List<String> anchors = new ArrayList<>();
        List<String> lines = new ArrayList<>(List.of(markdown.split("\n", -1)));
        int[] counters = new int[7];
        boolean inCode = false;

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.startsWith("```")) {
                inCode = !inCode;
                continue;
            }
            if (inCode) continue;

            Matcher matcher = HEADING.matcher(line);
            if (!matcher.matches()) continue;

            int level = matcher.group(1).length();
            String text = matcher.group(2);

            String anchor = null;
            Matcher explicit = EXPLICIT_ANCHOR.matcher(text);
            if (explicit.find()) {
                anchor = explicit.group(1);
                text = text.substring(0, explicit.start()).trim();
            }
            text = LEADING_NUMBER.matcher(text).replaceFirst("").trim();
            if (anchor == null) anchor = slug(text);
            anchor = unique(anchor, headings.keySet());

            String number = level < TOP_LEVEL ? "" : number(counters, level, style, paragraphSign);
            headings.put(anchor, new Heading(anchor, number, text));
            anchors.add(anchor);
            lines.set(i, matcher.group(1) + " " + (number.isEmpty() ? text : number + " " + text));
        }

        Set<String> unresolved = new LinkedHashSet<>();
        String resolved = resolveReferences(String.join("\n", lines), headings, unresolved);
        return new Result(resolved, List.copyOf(anchors), Set.copyOf(unresolved));
    }

    /**
     * Returns the anchors a document refers to, whether or not they exist. Used by the editor to
     * show what a section depends on before it is switched off or deleted.
     *
     * @param markdown the document to scan
     * @return every anchor named by a reference, in the order they appear
     */
    public static Set<String> referencedAnchors(String markdown) {
        Set<String> referenced = new LinkedHashSet<>();
        if (markdown == null) return referenced;
        Matcher matcher = REFERENCE.matcher(markdown);
        while (matcher.find()) {
            referenced.add(matcher.group(2));
        }
        return referenced;
    }

    /**
     * Turns a heading into an anchor: lowercase, umlauts written out, everything else a hyphen.
     *
     * @param text the heading without its number
     * @return the anchor derived from it
     */
    public static String slug(String text) {
        String normalised = text.toLowerCase(Locale.GERMAN)
                .replace("ä", "ae")
                .replace("ö", "oe")
                .replace("ü", "ue")
                .replace("ß", "ss")
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
        return normalised.isEmpty() ? "abschnitt" : normalised;
    }

    private static String unique(String anchor, Set<String> taken) {
        if (!taken.contains(anchor)) return anchor;
        int suffix = 2;
        while (taken.contains(anchor + "-" + suffix)) suffix++;
        return anchor + "-" + suffix;
    }

    private static String number(int[] counters, int level, Style style, String paragraphSign) {
        if (style == Style.NONE) return "";
        counters[level]++;
        for (int deeper = level + 1; deeper < counters.length; deeper++) {
            counters[deeper] = 0;
        }
        var digits = new StringBuilder();
        for (int current = TOP_LEVEL; current <= level; current++) {
            if (!digits.isEmpty()) digits.append('.');
            digits.append(counters[current]);
        }
        if (style == Style.PARAGRAPH && level == TOP_LEVEL) return paragraphSign + " " + digits;
        return digits + (level == TOP_LEVEL ? "." : "");
    }

    private static String resolveReferences(String markdown, Map<String, Heading> headings, Set<String> unresolved) {
        Matcher matcher = REFERENCE.matcher(markdown);
        var out = new StringBuilder();
        while (matcher.find()) {
            boolean withTitle = "!".equals(matcher.group(1));
            String anchor = matcher.group(2);
            Heading heading = headings.get(anchor);
            String replacement;
            if (heading == null) {
                unresolved.add(anchor);
                replacement = "**[Verweis fehlt: " + anchor + "]**";
            } else {
                String label = heading.number().isEmpty() ? heading.title() : trimDot(heading.number());
                if (withTitle) label = label + " (" + heading.title() + ")";
                replacement = "[" + label + "](#" + anchor + ")";
            }
            matcher.appendReplacement(out, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    private static String trimDot(String number) {
        return number.endsWith(".") ? number.substring(0, number.length() - 1) : number;
    }
}
