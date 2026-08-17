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
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Takes a document written elsewhere - by a lawyer, in a word processor - and turns it into the
 * form Ember maintains: sections as files, headings without numbers, and cross-references that
 * point at anchors rather than at numbers.
 *
 * <p>The numbers a legal text carries are the very thing that goes stale, so they are read once
 * and then dropped: {@code ## § 12 Nutzungsregeln} becomes {@code ## Nutzungsregeln} with the
 * anchor {@code nutzungsregeln}, and every {@code § 12} in the running text becomes
 * {@code {{ ref:nutzungsregeln }}}. From then on the document renumbers itself.
 *
 * <p>Nothing is guessed. A number in the text that matches no heading is left exactly as it was
 * and reported through {@link Imported#unmatched()}, so whoever imports the document can look at
 * it rather than discover it later in the published text.
 */
public final class LegalImportService {

    /**
     * One section of an imported document, ready to be written into the document directory.
     *
     * @param fileName    the file it becomes, ordered and slugged
     * @param displayName the section name shown in the editor
     * @param content     the markdown of the section, references already rewritten
     */
    public record Section(String fileName, String displayName, String content) {}

    /**
     * The outcome of an import.
     *
     * @param title      the document title, if the source carried one
     * @param sections   the sections in the order they were found
     * @param references how many numbers were turned into references
     * @param unmatched  numbers that look like a reference but match no heading, as they appear
     */
    public record Imported(String title, List<Section> sections, int references, Set<String> unmatched) {}

    private static final Pattern HEADING = Pattern.compile("^(#{1,6})\\s+(.*?)\\s*$");

    /**
     * The number a heading may lead with, in the spellings legal documents use.
     */
    private static final Pattern HEADING_NUMBER = Pattern.compile(
            "^(?:(?:§+|Art\\.?|Artikel|Section|Clause|Ziffer|Nr\\.?)\\s*)?(\\d+(?:\\.\\d+)*)[.)]?\\s+(.*)$");

    /**
     * A reference in the running text. The keyword is required here - a bare number in a sentence
     * is a number, not a cross-reference.
     */
    private static final Pattern TEXT_REFERENCE = Pattern.compile(
            "(§§?|Art\\.|Artikel|Section|Clause|Ziffer|Abschnitt|Nr\\.)\\s*(\\d+(?:\\.\\d+)*)",
            Pattern.CASE_INSENSITIVE);

    /**
     * Ordering prefixes are written in steps of ten so a later insert fits between two sections
     * without renaming its neighbours.
     */
    private static final int ORDER_STEP = 10;

    private LegalImportService() {}

    /**
     * Normalises a document into sections, anchors and references.
     *
     * @param markdown the document as markdown, converted from whatever it arrived as
     * @return the sections to write, and what could not be matched
     */
    public static Imported normalise(String markdown) {
        if (markdown == null || markdown.isBlank()) {
            return new Imported(null, List.of(), 0, Set.of());
        }
        List<String> lines = List.of(markdown.replace("\r\n", "\n").split("\n", -1));

        int sectionLevel = sectionLevel(lines);
        Map<String, String> numberToAnchor = new LinkedHashMap<>();
        Set<String> anchors = new LinkedHashSet<>();
        String title = null;

        // First pass: learn the numbers, so a reference can point backwards as well as forwards.
        List<String> rewritten = new ArrayList<>(lines.size());
        boolean inCode = false;
        for (String line : lines) {
            if (line.startsWith("```")) inCode = !inCode;
            Matcher heading = inCode ? null : HEADING.matcher(line);
            if (heading == null || !heading.matches()) {
                rewritten.add(line);
                continue;
            }
            int level = heading.group(1).length();
            String text = heading.group(2);
            Matcher numbered = HEADING_NUMBER.matcher(text);
            String number = null;
            if (numbered.matches()) {
                number = numbered.group(1);
                text = numbered.group(2).trim();
            }
            if (level == 1 && title == null) {
                title = text;
                rewritten.add("# " + text);
                continue;
            }
            String anchor = unique(LegalNumbering.slug(text), anchors);
            anchors.add(anchor);
            if (number != null) numberToAnchor.put(number, anchor);
            rewritten.add(heading.group(1) + " " + text + " {#" + anchor + "}");
        }

        int[] replaced = {0};
        Set<String> unmatched = new LinkedHashSet<>();
        List<String> resolved = new ArrayList<>(rewritten.size());
        for (String line : rewritten) {
            resolved.add(
                    HEADING.matcher(line).matches()
                            ? line
                            : rewriteReferences(line, numberToAnchor, replaced, unmatched));
        }

        return new Imported(title, split(resolved, sectionLevel), replaced[0], Set.copyOf(unmatched));
    }

    /**
     * The heading level the sections sit on: the shallowest level below the document title.
     */
    private static int sectionLevel(List<String> lines) {
        int level = 6;
        boolean sawTitle = false;
        for (String line : lines) {
            Matcher heading = HEADING.matcher(line);
            if (!heading.matches()) continue;
            int current = heading.group(1).length();
            if (current == 1 && !sawTitle) {
                sawTitle = true;
                continue;
            }
            level = Math.min(level, current);
        }
        return level == 6 && !sawTitle ? 1 : level;
    }

    private static String rewriteReferences(
            String line, Map<String, String> numberToAnchor, int[] replaced, Set<String> unmatched) {
        Matcher matcher = TEXT_REFERENCE.matcher(line);
        var out = new StringBuilder();
        while (matcher.find()) {
            String number = matcher.group(2);
            String anchor = numberToAnchor.get(number);
            if (anchor == null) {
                unmatched.add(matcher.group());
                matcher.appendReplacement(out, Matcher.quoteReplacement(matcher.group()));
                continue;
            }
            replaced[0]++;
            matcher.appendReplacement(out, Matcher.quoteReplacement("{{ ref:" + anchor + " }}"));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    /**
     * Cuts the document at its section headings. Everything before the first one belongs to the
     * document itself and is kept as its opening section.
     */
    private static List<Section> split(List<String> lines, int sectionLevel) {
        List<String> names = new ArrayList<>();
        List<String> contents = new ArrayList<>();
        var current = new StringBuilder();
        String currentName = null;
        String prefix = "#".repeat(sectionLevel) + " ";

        for (String line : lines) {
            if (line.startsWith(prefix)) {
                collect(names, contents, currentName, current.toString());
                current.setLength(0);
                currentName = sectionName(line);
            }
            current.append(line).append('\n');
        }
        collect(names, contents, currentName, current.toString());

        // A title on its own is not a section: it belongs to the one that follows it.
        if (names.size() > 1 && names.getFirst() == null && isTitleOnly(contents.getFirst())) {
            contents.set(1, contents.getFirst().strip() + "\n\n" + contents.get(1));
            names.removeFirst();
            contents.removeFirst();
        }

        List<Section> sections = new ArrayList<>();
        for (int i = 0; i < names.size(); i++) {
            String displayName = names.get(i) == null ? "einleitung" : names.get(i);
            sections.add(new Section(
                    "%03d-%s.md".formatted((i + 1) * ORDER_STEP, displayName),
                    displayName,
                    contents.get(i).strip() + "\n"));
        }
        return List.copyOf(sections);
    }

    private static void collect(List<String> names, List<String> contents, String name, String content) {
        if (content.isBlank()) return;
        names.add(name);
        contents.add(content);
    }

    /**
     * Whether a block carries nothing but headings, which is what a bare document title looks like.
     */
    private static boolean isTitleOnly(String content) {
        return content.lines().noneMatch(line -> !line.isBlank() && !line.startsWith("#"));
    }

    private static String sectionName(String headingLine) {
        Matcher heading = HEADING.matcher(headingLine);
        if (!heading.matches()) return "abschnitt";
        String text = heading.group(2).replaceAll("\\s*\\{#[A-Za-z0-9_.\\-]+}\\s*$", "");
        return LegalNumbering.slug(text);
    }

    private static String unique(String anchor, Set<String> taken) {
        if (!taken.contains(anchor)) return anchor;
        int suffix = 2;
        while (taken.contains(anchor + "-" + suffix)) suffix++;
        return anchor + "-" + suffix;
    }
}
