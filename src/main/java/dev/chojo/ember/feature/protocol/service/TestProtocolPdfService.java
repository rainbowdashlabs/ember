/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.protocol.service;

import dev.chojo.ember.feature.account.repository.AccountRepository;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import dev.chojo.ember.feature.protocol.entity.TestProtocolItem;
import dev.chojo.ember.feature.protocol.entity.TestProtocolRunCheck;
import dev.chojo.ember.feature.protocol.entity.TestProtocolSection;
import dev.chojo.ember.feature.protocol.repository.TestProtocolRepository;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.repository.StationRepository;
import dev.chojo.ember.util.TypstCompiler;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Singleton
public class TestProtocolPdfService {

    private final TestProtocolRepository repository;
    private final StationMemberRepository memberRepository;
    private final AccountRepository accountRepository;
    private final StationRepository stationRepository;

    @Inject
    public TestProtocolPdfService(
            TestProtocolRepository repository,
            StationMemberRepository memberRepository,
            AccountRepository accountRepository,
            StationRepository stationRepository) {
        this.repository = repository;
        this.memberRepository = memberRepository;
        this.accountRepository = accountRepository;
        this.stationRepository = stationRepository;
    }

    // ── Per-member PDF ──────────────────────────────────────────

    public byte[] exportRunMember(int runId, int memberId, String protocolName, LocalDate testDate) {
        var rm = repository
                .findRunMember(runId, memberId)
                .orElseThrow(() -> new IllegalArgumentException("Member not found in run"));
        var checks = repository.findChecks(rm.id());
        var checkedItems = checks.stream()
                .filter(TestProtocolRunCheck::checked)
                .map(TestProtocolRunCheck::itemId)
                .collect(Collectors.toSet());

        var itemTesterMap = new HashMap<Integer, Integer>();
        for (var c : checks) {
            if (c.checked() && c.checkedBy() != null) itemTesterMap.put(c.itemId(), c.checkedBy());
        }

        var run = repository.findRunById(runId).orElseThrow();
        var sections = repository.findSections(run.protocolId());
        var allItems = repository.findAllItemsByProtocol(run.protocolId());
        var itemsBySectionId = allItems.stream().collect(Collectors.groupingBy(TestProtocolItem::sectionId));

        String memberName = resolveMemberName(memberId);
        String stationName =
                stationRepository.findById(run.stationId()).map(Station::name).orElse("");

        var resources = loadLogo();
        var sb = new StringBuilder();
        sb.append("""
                #set page(paper: "a4", flipped: true, margin: 1.5cm, columns: 2)
                #set text(font: "Noto Sans", size: 9pt, lang: "de")

                """);

        // Header
        sb.append("#align(center)[\n");
        if (resources.containsKey("logo.png")) {
            sb.append("  #grid(columns: (auto, 1fr), gutter: 0.5em, align: (center, left),\n");
            sb.append("    image(\"logo.png\", width: 1.2cm),\n");
            sb.append("    align(horizon)[#text(size: 10pt, weight: \"bold\")[")
                    .append(esc(stationName))
                    .append("]]\n");
            sb.append("  )\n  #v(0.3em)\n");
        }
        sb.append("  #text(size: 14pt, weight: \"bold\")[")
                .append(esc(protocolName))
                .append("]\n");
        sb.append("  #v(0.3em)\n");
        sb.append("  Name: *")
                .append(esc(memberName))
                .append("* #h(2em) Datum: ")
                .append(testDate)
                .append("\n");
        sb.append("  #v(0.5em)\n]\n\n");

        var topSections = sections.stream()
                .filter(s -> s.parentId() == null)
                .sorted(Comparator.comparingInt(TestProtocolSection::position))
                .toList();

        double totalScore = 0, totalMax = 0;
        for (var section : topSections) {
            var r = renderSection(sb, section, sections, itemsBySectionId, checkedItems, itemTesterMap, 0);
            totalScore += r.score;
            totalMax += r.max;
            sb.append("\n");
        }

        sb.append("#v(0.5em)\n#line(length: 100%)\n");
        sb.append("#text(size: 11pt, weight: \"bold\")[Gesamt: ")
                .append(fmt(totalScore))
                .append(" \\/ ")
                .append(fmt(totalMax))
                .append(" Punkte]\n\n");
        sb.append("#v(1.5em)\nUnterschrift: #box(width: 6cm, stroke: (bottom: 0.5pt))[]\n");

        try {
            return TypstCompiler.compile(sb.toString(), resources);
        } catch (Exception e) {
            throw new RuntimeException("PDF export failed", e);
        }
    }

    // ── Evaluation table PDF ────────────────────────────────────

    public byte[] exportEvaluationTable(int runId, String protocolName, LocalDate testDate) {
        var run = repository.findRunById(runId).orElseThrow();
        var sections = repository.findSections(run.protocolId());
        var allItems = repository.findAllItemsByProtocol(run.protocolId());
        var members = repository.findRunMembers(runId);
        var itemsBySectionId = allItems.stream().collect(Collectors.groupingBy(TestProtocolItem::sectionId));

        record MS(int memberId, String name, Map<Integer, Double> scores, double total) {}
        var data = new ArrayList<MS>();
        for (var rm : members) {
            var checks = repository.findChecks(rm.id());
            var checkedIds = checks.stream()
                    .filter(TestProtocolRunCheck::checked)
                    .map(TestProtocolRunCheck::itemId)
                    .collect(Collectors.toSet());
            var sc = new HashMap<Integer, Double>();
            for (var sec : sections) {
                sc.put(
                        sec.id(),
                        itemsBySectionId.getOrDefault(sec.id(), List.of()).stream()
                                .filter(i -> checkedIds.contains(i.id()))
                                .mapToDouble(TestProtocolItem::points)
                                .sum());
            }
            data.add(new MS(rm.memberId(), resolveMemberName(rm.memberId()), sc, rm.totalScore()));
        }
        var allScores = data.stream().map(MS::scores).toList();
        var topSections = sections.stream()
                .filter(s -> s.parentId() == null)
                .sorted(Comparator.comparingInt(TestProtocolSection::position))
                .toList();

        String stationName =
                stationRepository.findById(run.stationId()).map(Station::name).orElse("");
        var resources = loadLogo();
        var sb = new StringBuilder();
        sb.append("""
                #set page(paper: "a4", flipped: true, margin: 1cm)
                #set text(font: "Noto Sans", size: 7pt, lang: "de")
                #let score-fill(score, max) = {
                  if max == 0 { white } else {
                    let ratio = score / max
                    if ratio >= 0.9 { rgb("#dcfce7") }
                    else if ratio >= 0.6 { rgb("#fef9c3") }
                    else if ratio >= 0.3 { rgb("#ffedd5") }
                    else { rgb("#fee2e2") }
                  }
                }

                """);

        // Header
        sb.append("#align(center)[\n");
        if (resources.containsKey("logo.png")) {
            sb.append("  #box(image(\"logo.png\", width: 1cm)) #h(0.5em) #text(size: 9pt, weight: \"bold\")[")
                    .append(esc(stationName))
                    .append("]\n  #v(0.3em)\n");
        }
        sb.append("  #text(size: 11pt, weight: \"bold\")[")
                .append(esc(protocolName))
                .append(" — Auswertung]\n");
        sb.append("  #v(0.2em)\n  #text(size: 8pt)[Datum: ").append(testDate).append("]\n]\n#v(0.3em)\n\n");

        // Table
        sb.append("#table(\n  columns: (auto, 3em, 3em");
        sb.repeat(", 1fr", data.size());
        sb.append("),\n  align: (left, center, center");
        sb.repeat(", center", data.size());
        sb.append("),\n  stroke: 0.3pt + luma(180),\n");

        // Header row
        sb.append("  table.header([*Thema*], [*Max*], [*Ø*]");
        for (var m : data) sb.append(", [*").append(esc(m.name())).append("*]");
        sb.append("),\n");

        for (var sec : topSections) {
            // Subsection detail rows first
            for (var sub : sections.stream()
                    .filter(s -> s.parentId() != null && s.parentId() == sec.id())
                    .sorted(Comparator.comparingInt(TestProtocolSection::position))
                    .toList()) {
                double subMax = secMax(sub, sections, itemsBySectionId);
                double subAvg = avg(allScores, sub, sections);
                sb.append("  [#h(0.5em)#text(size: 6.5pt)[")
                        .append(esc(sub.name()))
                        .append("]],\n");
                sb.append("  [#text(size: 6.5pt)[").append(fmt(subMax)).append("]],\n");
                colorCell(sb, subAvg, subMax);
                for (var m : data) colorCell(sb, secScore(m.scores(), sub, sections), subMax);
            }
            // Top-level section sum row (bold, gray, acts as sum separator)
            double sMax = secMax(sec, sections, itemsBySectionId);
            double sAvg = avg(allScores, sec, sections);
            sb.append("  table.cell(fill: luma(235))[*").append(esc(sec.name())).append("*],\n");
            sb.append("  table.cell(fill: luma(235))[*").append(fmt(sMax)).append("*],\n");
            boldColorCell(sb, sAvg, sMax);
            for (var m : data) boldColorCell(sb, secScore(m.scores(), sec, sections), sMax);
            sb.append("  table.hline(stroke: 0.8pt),\n");
        }

        double tMax = topSections.stream()
                .mapToDouble(s -> secMax(s, sections, itemsBySectionId))
                .sum();
        double tAvg = data.isEmpty() ? 0 : data.stream().mapToDouble(MS::total).sum() / data.size();
        sb.append("  table.cell(fill: luma(210))[*Gesamt*],\n");
        sb.append("  table.cell(fill: luma(210))[*").append(fmt(tMax)).append("*],\n");
        boldColorCell(sb, tAvg, tMax);
        for (var m : data) boldColorCell(sb, m.total(), tMax);
        sb.append(")\n");

        try {
            return TypstCompiler.compile(sb.toString(), resources);
        } catch (Exception e) {
            throw new RuntimeException("Evaluation PDF export failed", e);
        }
    }

    // ── Helpers ─────────────────────────────────────────────────

    private record SectionResult(double score, double max) {}

    private SectionResult renderSection(
            StringBuilder sb,
            TestProtocolSection section,
            List<TestProtocolSection> allSections,
            Map<Integer, List<TestProtocolItem>> itemsBySectionId,
            Set<Integer> checkedItems,
            Map<Integer, Integer> itemTesterMap,
            int depth) {

        double sScore = 0, sMax = 0;
        var sectionItems = itemsBySectionId.getOrDefault(section.id(), List.of()).stream()
                .sorted(Comparator.comparingInt(TestProtocolItem::position))
                .toList();
        for (var item : sectionItems) {
            sMax += item.points();
            if (checkedItems.contains(item.id())) sScore += item.points();
        }
        var children = allSections.stream()
                .filter(s -> s.parentId() != null && s.parentId() == section.id())
                .sorted(Comparator.comparingInt(TestProtocolSection::position))
                .toList();

        // Collect testers
        var testerIds = new LinkedHashSet<Integer>();
        for (var item : sectionItems)
            if (itemTesterMap.containsKey(item.id())) testerIds.add(itemTesterMap.get(item.id()));
        for (var child : children)
            for (var item : itemsBySectionId.getOrDefault(child.id(), List.of()))
                if (itemTesterMap.containsKey(item.id())) testerIds.add(itemTesterMap.get(item.id()));

        // Calculate child scores first so we have the total for the header
        double cScore = 0, cMax = 0;
        // We'll render children after the header, but need totals now
        for (var child : children) {
            double cm = 0;
            for (var ci : itemsBySectionId.getOrDefault(child.id(), List.of())) cm += ci.points();
            cMax += cm;
            // child score needs checked items
            for (var ci : itemsBySectionId.getOrDefault(child.id(), List.of()))
                if (checkedItems.contains(ci.id())) cScore += ci.points();
        }
        double totalScore = sScore + cScore;
        double totalMax = sMax + cMax;

        // Section header as table: Name | Prüfer (top-level only) | Score
        if (depth == 0) {
            String testers = testerIds.stream().map(this::resolveMemberName).collect(Collectors.joining(", "));
            sb.append("#v(0.4em)\n");
            sb.append("#line(length: 100%, stroke: 0.5pt)\n");
            sb.append("#table(columns: (1fr, auto, auto), stroke: none, inset: 3pt,\n");
            sb.append("  [#text(size: 11pt, weight: \"bold\")[")
                    .append(esc(section.name()))
                    .append("]],\n");
            sb.append("  [#text(size: 8pt)[Prüfer: ")
                    .append(esc(testers.isEmpty() ? "—" : testers))
                    .append("]],\n");
            sb.append("  align(right)[#text(weight: \"bold\")[")
                    .append(fmt(totalScore))
                    .append(" \\/ ")
                    .append(fmt(totalMax))
                    .append("P]]\n");
            sb.append(")\n");
        } else {
            sb.append("#v(0.2em)\n");
            sb.append("#table(columns: (1fr, auto), stroke: none, inset: 2pt,\n");
            sb.append("  [#text(size: 9pt, weight: \"bold\")[")
                    .append(esc(section.name()))
                    .append("]],\n");
            sb.append("  align(right)[")
                    .append(fmt(totalScore))
                    .append(" \\/ ")
                    .append(fmt(totalMax))
                    .append("P]\n");
            sb.append(")\n");
        }

        // Items
        for (var item : sectionItems) {
            boolean checked = checkedItems.contains(item.id());
            sb.append(
                    checked
                            ? "#box(stroke: 0.5pt, width: 10pt, height: 10pt, align(center + horizon)[#text(size: 7pt)[✓]])"
                            : "#box(stroke: 0.5pt, width: 10pt, height: 10pt)[]");
            sb.append(" ")
                    .append(esc(item.label()))
                    .append(" #h(1fr) ")
                    .append(fmt(item.points()))
                    .append("P\n\n");
        }

        // Render children (actual recursive call for their content)
        for (var child : children) {
            renderSection(sb, child, allSections, itemsBySectionId, checkedItems, itemTesterMap, depth + 1);
        }

        return new SectionResult(totalScore, totalMax);
    }

    private double secMax(
            TestProtocolSection sec, List<TestProtocolSection> all, Map<Integer, List<TestProtocolItem>> items) {
        double d = items.getOrDefault(sec.id(), List.of()).stream()
                .mapToDouble(TestProtocolItem::points)
                .sum();
        double c = all.stream()
                .filter(s -> s.parentId() != null && s.parentId() == sec.id())
                .mapToDouble(s -> secMax(s, all, items))
                .sum();
        return d + c;
    }

    private double secScore(Map<Integer, Double> scores, TestProtocolSection sec, List<TestProtocolSection> all) {
        double d = scores.getOrDefault(sec.id(), 0.0);
        double c = all.stream()
                .filter(s -> s.parentId() != null && s.parentId() == sec.id())
                .mapToDouble(s -> scores.getOrDefault(s.id(), 0.0))
                .sum();
        return d + c;
    }

    private double avg(List<Map<Integer, Double>> allScores, TestProtocolSection sec, List<TestProtocolSection> all) {
        if (allScores.isEmpty()) return 0;
        return allScores.stream().mapToDouble(s -> secScore(s, sec, all)).sum() / allScores.size();
    }

    private void colorCell(StringBuilder sb, double score, double max) {
        sb.append("  table.cell(fill: score-fill(")
                .append(fmt(score))
                .append(", ")
                .append(fmt(max))
                .append("))[")
                .append(fmt(score))
                .append("],\n");
    }

    private void boldColorCell(StringBuilder sb, double score, double max) {
        sb.append("  table.cell(fill: score-fill(")
                .append(fmt(score))
                .append(", ")
                .append(fmt(max))
                .append("))[*")
                .append(fmt(score))
                .append("*],\n");
    }

    private String fmt(double val) {
        if (val == (long) val) return String.valueOf((long) val);
        return String.format("%.1f", val);
    }

    private String esc(String text) {
        return text.replace("\\", "\\\\")
                .replace("#", "\\#")
                .replace("*", "\\*")
                .replace("_", "\\_")
                .replace("[", "\\[")
                .replace("]", "\\]");
    }

    private Map<String, byte[]> loadLogo() {
        var resources = new HashMap<String, byte[]>();
        try (var stream = getClass().getClassLoader().getResourceAsStream("logo/IconBG.png")) {
            if (stream != null) resources.put("logo.png", stream.readAllBytes());
        } catch (Exception ignored) {
        }
        return resources;
    }

    private String resolveMemberName(int memberId) {
        return memberRepository
                .findById(memberId)
                .map(m -> {
                    if (m.displayName() != null && !m.displayName().isBlank()) return m.displayName();
                    if (m.accountId() != null) {
                        return accountRepository
                                .findById(m.accountId())
                                .map(a -> a.firstName() + " " + a.lastName())
                                .orElse("Unbekannt");
                    }
                    return "Unbekannt";
                })
                .orElse("Unbekannt");
    }
}
