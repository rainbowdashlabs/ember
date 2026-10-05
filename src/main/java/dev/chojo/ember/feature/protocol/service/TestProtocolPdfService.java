/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.protocol.service;

import dev.chojo.ember.feature.account.repository.AccountRepository;
import dev.chojo.ember.feature.members.entity.NameParts;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import dev.chojo.ember.feature.protocol.entity.TestProtocolItem;
import dev.chojo.ember.feature.protocol.entity.TestProtocolRunCheck;
import dev.chojo.ember.feature.protocol.entity.TestProtocolSection;
import dev.chojo.ember.feature.protocol.repository.TestProtocolRepository;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.entity.StationFormat;
import dev.chojo.ember.feature.station.repository.StationRepository;
import dev.chojo.ember.feature.station.service.StationLogoService;
import dev.chojo.ember.util.DocumentNumber;
import dev.chojo.ember.util.TypstCompiler;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Prints a test protocol run: one sheet per member with every item ticked or not, and one
 * evaluation table across all members. Both are templates in the station's document language,
 * fed with the run as data, so nothing a section, an item or a name says is read as markup.
 *
 * <p>A member whose name cannot be resolved goes to the template as {@code null}, and the
 * template prints its own word for "unknown".
 */
@Singleton
public class TestProtocolPdfService {
    private static final Logger log = LoggerFactory.getLogger(TestProtocolPdfService.class);
    private static final String MEMBER_TEMPLATE = "protocol-member.typ";
    private static final String EVALUATION_TEMPLATE = "protocol-evaluation.typ";

    private final TestProtocolRepository repository;
    private final StationMemberRepository memberRepository;
    private final AccountRepository accountRepository;
    private final StationRepository stationRepository;
    private final StationLogoService logoService;

    @Inject
    public TestProtocolPdfService(
            TestProtocolRepository repository,
            StationMemberRepository memberRepository,
            AccountRepository accountRepository,
            StationRepository stationRepository,
            StationLogoService logoService) {
        this.repository = repository;
        this.memberRepository = memberRepository;
        this.accountRepository = accountRepository;
        this.stationRepository = stationRepository;
        this.logoService = logoService;
    }

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
        var sheet = new MemberSheet(
                sections,
                allItems.stream().collect(Collectors.groupingBy(TestProtocolItem::sectionId)),
                checkedItems,
                itemTesterMap,
                localeOf(run.stationId()));

        var sheetSections = new ArrayList<SheetSection>();
        double totalScore = 0;
        double totalMax = 0;
        for (var section : sheet.childrenOf(null)) {
            var rendered = sheet.section(section, true);
            totalScore += rendered.score();
            totalMax += rendered.max();
            sheetSections.add(rendered.section());
        }

        var data = header(run.stationId(), protocolName, testDate);
        data.put("memberName", resolveMemberName(memberId));
        data.put("sections", sheetSections);
        data.put("totalScore", sheet.number(totalScore));
        data.put("totalMax", sheet.number(totalMax));

        try {
            return render(run.stationId(), MEMBER_TEMPLATE, data);
        } catch (Exception e) {
            log.warn("PDF export failed for run {} member {}", runId, memberId, e);
            throw new RuntimeException("PDF export failed", e);
        }
    }

    public byte[] exportEvaluationTable(int runId, String protocolName, LocalDate testDate) {
        var run = repository.findRunById(runId).orElseThrow();
        var sections = repository.findSections(run.protocolId());
        var allItems = repository.findAllItemsByProtocol(run.protocolId());
        var members = repository.findRunMembers(runId);
        var itemsBySectionId = allItems.stream().collect(Collectors.groupingBy(TestProtocolItem::sectionId));
        Locale locale = localeOf(run.stationId());

        var data = new ArrayList<MS>();
        var memberNames = new ArrayList<String>();
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
            data.add(new MS(sc, rm.totalScore()));
            memberNames.add(resolveMemberName(rm.memberId()));
        }
        var allScores = data.stream().map(MS::scores).toList();
        var topSections = sections.stream()
                .filter(s -> s.parentId() == null)
                .sorted(Comparator.comparingInt(TestProtocolSection::position))
                .toList();

        var rows = new ArrayList<EvaluationRow>();
        for (var sec : topSections) {
            addDetailRows(rows, sec, 1, sections, itemsBySectionId, allScores, data, locale);
            double sMax = secMax(sec, sections, itemsBySectionId);
            var cells = new ArrayList<ScoreCell>();
            cells.add(cell(avg(allScores, sec, sections), sMax, locale));
            for (var m : data) cells.add(cell(secScore(m.scores(), sec, sections), sMax, locale));
            rows.add(new EvaluationRow(RowKind.SECTION, sec.name(), DocumentNumber.of(sMax, locale), cells, 0));
        }

        double tMax = topSections.stream()
                .mapToDouble(s -> secMax(s, sections, itemsBySectionId))
                .sum();
        double tAvg = data.isEmpty() ? 0 : data.stream().mapToDouble(MS::total).sum() / data.size();
        var totalCells = new ArrayList<ScoreCell>();
        totalCells.add(cell(tAvg, tMax, locale));
        for (var m : data) totalCells.add(cell(m.total(), tMax, locale));
        rows.add(new EvaluationRow(RowKind.TOTAL, null, DocumentNumber.of(tMax, locale), totalCells, 0));

        var document = header(run.stationId(), protocolName, testDate);
        document.put("members", memberNames);
        document.put("rows", rows);

        try {
            return render(run.stationId(), EVALUATION_TEMPLATE, document);
        } catch (Exception e) {
            log.warn("Evaluation PDF export failed for run {}", runId, e);
            throw new RuntimeException("Evaluation PDF export failed", e);
        }
    }

    private Map<String, Object> header(int stationId, String protocolName, LocalDate testDate) {
        var data = new LinkedHashMap<String, Object>();
        data.put(
                "stationName",
                stationRepository.findById(stationId).map(Station::name).orElse(""));
        data.put("protocolName", protocolName);
        data.put("testDate", testDate == null ? "" : testDate.toString());
        data.put("hasLogo", false);
        return data;
    }

    private byte[] render(int stationId, String template, Map<String, Object> data) throws Exception {
        String language =
                StationFormat.languageOf(stationRepository.findById(stationId).orElse(null));
        return TypstCompiler.compileTemplate(data, language + "/" + template, loadLogo(stationId));
    }

    private Locale localeOf(int stationId) {
        return StationFormat.localeOf(stationRepository.findById(stationId).orElse(null));
    }

    private static ScoreCell cell(double score, double max, Locale locale) {
        return new ScoreCell(score, max, DocumentNumber.of(score, locale));
    }

    private double secMax(
            TestProtocolSection sec, List<TestProtocolSection> all, Map<Integer, List<TestProtocolItem>> items) {
        double d = items.getOrDefault(sec.id(), List.of()).stream()
                .mapToDouble(TestProtocolItem::maxPoints)
                .sum();
        double c = all.stream()
                .filter(s -> Objects.equals(s.parentId(), sec.id()))
                .mapToDouble(s -> secMax(s, all, items))
                .sum();
        return d + c;
    }

    private double secScore(Map<Integer, Double> scores, TestProtocolSection sec, List<TestProtocolSection> all) {
        double d = scores.getOrDefault(sec.id(), 0.0);
        double c = all.stream()
                .filter(s -> Objects.equals(s.parentId(), sec.id()))
                .mapToDouble(s -> secScore(scores, s, all))
                .sum();
        return d + c;
    }

    /**
     * Adds a detail row for every section under the given one, depth first, each indented by how deep
     * it sits, so the sum row of a top-level section stands below everything it adds up.
     */
    private void addDetailRows(
            List<EvaluationRow> rows,
            TestProtocolSection parent,
            int depth,
            List<TestProtocolSection> all,
            Map<Integer, List<TestProtocolItem>> items,
            List<Map<Integer, Double>> allScores,
            List<MS> data,
            Locale locale) {
        var children = all.stream()
                .filter(s -> Objects.equals(s.parentId(), parent.id()))
                .sorted(Comparator.comparingInt(TestProtocolSection::position))
                .toList();
        for (var sub : children) {
            double subMax = secMax(sub, all, items);
            var cells = new ArrayList<ScoreCell>();
            cells.add(cell(avg(allScores, sub, all), subMax, locale));
            for (var m : data) cells.add(cell(secScore(m.scores(), sub, all), subMax, locale));
            rows.add(new EvaluationRow(RowKind.DETAIL, sub.name(), DocumentNumber.of(subMax, locale), cells, depth));
            addDetailRows(rows, sub, depth + 1, all, items, allScores, data, locale);
        }
    }

    private double avg(List<Map<Integer, Double>> allScores, TestProtocolSection sec, List<TestProtocolSection> all) {
        if (allScores.isEmpty()) return 0;
        return allScores.stream().mapToDouble(s -> secScore(s, sec, all)).sum() / allScores.size();
    }

    /**
     * The station's logo, or the application's own when the station has none, or {@code null}
     * when not even that can be read.
     */
    private TypstCompiler.@Nullable StationLogo loadLogo(int stationId) {
        var stationLogo = logoService.original(stationId);
        if (stationLogo.isPresent()) {
            var logo = stationLogo.get();
            return new TypstCompiler.StationLogo(logo.data(), logo.contentType());
        }
        try (var stream = getClass().getClassLoader().getResourceAsStream("logo/IconBG.png")) {
            if (stream != null) return new TypstCompiler.StationLogo(stream.readAllBytes(), "image/png");
        } catch (Exception e) {
            log.warn("Fallback logo could not be read, the protocol is built without one", e);
        }
        return null;
    }

    /** The member's display name, else the official name of their account, else {@code null}. */
    private String resolveMemberName(int memberId) {
        return memberRepository
                .findById(memberId)
                .map(m -> {
                    if (m.displayName() != null && !m.displayName().isBlank()) return m.displayName();
                    Integer accountId = m.accountId();
                    if (accountId == null) return null;
                    return accountRepository
                            .findById(accountId)
                            .map(a -> NameParts.of(a).official())
                            .orElse(null);
                })
                .orElse(null);
    }

    /**
     * One member's run, turned into the nested sections the member sheet prints.
     *
     * <p>A section's header total counts its own items and those of every section under it, at any
     * depth, bonus points adding to the score but not to the maximum.
     */
    private final class MemberSheet {
        private final List<TestProtocolSection> sections;
        private final Map<Integer, List<TestProtocolItem>> itemsBySectionId;
        private final Set<Integer> checkedItems;
        private final Map<Integer, Integer> itemTesterMap;
        private final Locale locale;

        MemberSheet(
                List<TestProtocolSection> sections,
                Map<Integer, List<TestProtocolItem>> itemsBySectionId,
                Set<Integer> checkedItems,
                Map<Integer, Integer> itemTesterMap,
                Locale locale) {
            this.sections = sections;
            this.itemsBySectionId = itemsBySectionId;
            this.checkedItems = checkedItems;
            this.itemTesterMap = itemTesterMap;
            this.locale = locale;
        }

        String number(double value) {
            return DocumentNumber.of(value, locale);
        }

        List<TestProtocolSection> childrenOf(@Nullable Integer parentId) {
            return sections.stream()
                    .filter(s -> parentId == null ? s.parentId() == null : parentId.equals(s.parentId()))
                    .sorted(Comparator.comparingInt(TestProtocolSection::position))
                    .toList();
        }

        private List<TestProtocolItem> itemsOf(TestProtocolSection section) {
            return itemsBySectionId.getOrDefault(section.id(), List.of());
        }

        /** The items of a section and of every section under it, at any depth. */
        private List<TestProtocolItem> itemsUnder(TestProtocolSection section) {
            var all = new ArrayList<>(itemsOf(section));
            for (var child : childrenOf(section.id())) all.addAll(itemsUnder(child));
            return all;
        }

        /**
         * Builds a section with its items and children.
         *
         * @param topLevel whether it is a top-level section, the only kind that names its testers
         */
        RenderedSection section(TestProtocolSection section, boolean topLevel) {
            var sectionItems = itemsOf(section).stream()
                    .sorted(Comparator.comparingInt(TestProtocolItem::position))
                    .toList();
            var children = childrenOf(section.id());

            var items = sectionItems.stream()
                    .map(item -> new SheetItem(
                            item.label(), number(item.points()), checkedItems.contains(item.id()), item.bonus()))
                    .toList();

            double score = 0;
            double max = 0;
            var testerIds = new LinkedHashSet<Integer>();
            for (var item : itemsUnder(section)) {
                max += item.maxPoints();
                if (checkedItems.contains(item.id())) score += item.points();
                if (itemTesterMap.containsKey(item.id())) testerIds.add(itemTesterMap.get(item.id()));
            }

            List<String> testers = topLevel
                    ? testerIds.stream()
                            .map(TestProtocolPdfService.this::resolveMemberName)
                            .toList()
                    : List.of();
            var childSections = children.stream()
                    .map(child -> section(child, false).section())
                    .toList();
            return new RenderedSection(
                    new SheetSection(section.name(), testers, number(score), number(max), items, childSections),
                    score,
                    max);
        }
    }

    private record RenderedSection(SheetSection section, double score, double max) {}

    /** One member's points per section, each counting only the section's own items, and their total. */
    private record MS(Map<Integer, Double> scores, double total) {}

    /**
     * A section of the member sheet.
     *
     * @param name the section's name
     * @param testers who ticked its items, a {@code null} entry for a name that cannot be resolved;
     *         empty below the top level
     * @param score the points reached, formatted
     * @param max the points available, formatted
     * @param items its own items in order
     * @param children its subsections in order
     */
    public record SheetSection(
            String name,
            List<String> testers,
            String score,
            String max,
            List<SheetItem> items,
            List<SheetSection> children) {}

    /**
     * An item of the member sheet.
     *
     * @param label what was tested
     * @param points the points it is worth, formatted
     * @param checked whether the member passed it
     * @param bonus whether it is a bonus point, which the sheet marks as such
     */
    public record SheetItem(String label, String points, boolean checked, boolean bonus) {}

    /** How a row of the evaluation table is drawn. */
    public enum RowKind {
        DETAIL,
        SECTION,
        TOTAL
    }

    /**
     * A row of the evaluation table.
     *
     * @param kind a subsection's detail row, a top-level section's sum row, or the grand total
     * @param name the section's name, {@code null} for the total, which the template names
     * @param max the points available, formatted
     * @param cells the average first, then one cell per member in column order
     * @param depth how deep the section sits, zero for top-level sections and the total
     */
    public record EvaluationRow(RowKind kind, @Nullable String name, String max, List<ScoreCell> cells, int depth) {}

    /**
     * A score in the evaluation table, coloured by how much of the maximum it reaches.
     *
     * @param score the points reached, for the colour
     * @param max the points available, for the colour
     * @param label the points reached, formatted
     */
    public record ScoreCell(double score, double max, String label) {}
}
