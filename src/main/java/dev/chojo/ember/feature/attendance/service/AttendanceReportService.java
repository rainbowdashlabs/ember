/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.attendance.service;

import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.api.refusal.AttendanceRefusal;
import dev.chojo.ember.conf.file.elements.Api;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.account.repository.AccountRepository;
import dev.chojo.ember.feature.attendance.entity.AttendanceEntry;
import dev.chojo.ember.feature.attendance.entity.AttendanceReportPreset;
import dev.chojo.ember.feature.attendance.entity.AttendanceSession;
import dev.chojo.ember.feature.attendance.entity.ExtraTime;
import dev.chojo.ember.feature.attendance.entity.TimeRule;
import dev.chojo.ember.feature.attendance.repository.AttendanceRepository;
import dev.chojo.ember.feature.attendance.repository.AttendanceTimeRuleRepository;
import dev.chojo.ember.feature.media.entity.MediaContent;
import dev.chojo.ember.feature.members.entity.MemberGroup;
import dev.chojo.ember.feature.members.entity.NameParts;
import dev.chojo.ember.feature.members.repository.MemberGroupRepository;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import dev.chojo.ember.feature.restriction.RestrictionMember;
import dev.chojo.ember.feature.restriction.service.RestrictionService;
import dev.chojo.ember.feature.station.entity.StationFormat;
import dev.chojo.ember.feature.station.repository.StationRepository;
import dev.chojo.ember.feature.station.service.StationLogoService;
import dev.chojo.ember.util.CsvWriter;
import dev.chojo.ember.util.DocumentName;
import dev.chojo.ember.util.DocumentPeriod;
import dev.chojo.ember.util.DocumentWord;
import dev.chojo.ember.util.ExportedDocument;
import dev.chojo.ember.util.TypstCompiler;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import java.io.IOException;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import static org.slf4j.LoggerFactory.getLogger;

/**
 * Service for building attendance reports, managing report presets, and exporting reports as PDF.
 * Supports filtering by date range, groups, and members, with monthly aggregation.
 */
@Singleton
public class AttendanceReportService {
    private static final Logger log = getLogger(AttendanceReportService.class);
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final DateTimeFormatter SHEET_DAY_FMT = DateTimeFormatter.ofPattern("dd.MM.");
    private static final DateTimeFormatter SHEET_TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");
    private final AttendanceRepository attendanceRepository;
    private final StationMemberRepository stationMemberRepository;
    private final AccountRepository accountRepository;
    private final StationRepository stationRepository;
    private final MemberGroupRepository memberGroupRepository;
    private final Api apiConfig;
    private final StationLogoService logoService;
    private final AttendanceTimeRuleRepository timeRuleRepository;
    private final RestrictionService restrictionService;

    @Inject
    public AttendanceReportService(
            AttendanceRepository attendanceRepository,
            StationMemberRepository stationMemberRepository,
            AccountRepository accountRepository,
            StationRepository stationRepository,
            MemberGroupRepository memberGroupRepository,
            Api apiConfig,
            StationLogoService logoService,
            AttendanceTimeRuleRepository timeRuleRepository,
            RestrictionService restrictionService) {
        this.attendanceRepository = attendanceRepository;
        this.stationMemberRepository = stationMemberRepository;
        this.accountRepository = accountRepository;
        this.stationRepository = stationRepository;
        this.memberGroupRepository = memberGroupRepository;
        this.apiConfig = apiConfig;
        this.logoService = logoService;
        this.timeRuleRepository = timeRuleRepository;
        this.restrictionService = restrictionService;
    }

    /**
     * Retrieves all report presets for a station.
     */
    public List<AttendanceReportPreset> findPresets(int stationId) {
        return attendanceRepository.findPresets(stationId);
    }

    /**
     * Creates a new report preset holding every user type and every group the filter selects.
     *
     * <p>A missing list counts as an empty one, but a preset needs at least one user type or at
     * least one group: a filter that selects nobody is not worth saving.
     *
     * @throws dev.chojo.ember.api.refusal.RefusalResponse when the preset selects neither, or a list carries
     *                                             an empty entry
     */
    public AttendanceReportPreset createPreset(
            int stationId,
            String name,
            List<StationUserType> userTypes,
            List<Integer> groupIds,
            String period,
            String rounding) {
        var types = presetSelection(userTypes);
        var groups = presetSelection(groupIds);
        if (types.isEmpty() && groups.isEmpty()) {
            throw AttendanceRefusal.ATTENDANCE_REPORT_PRESET_SELECTS_NOBODY.raise();
        }
        var preset = attendanceRepository.createPreset(stationId, name, types, groups, period, rounding);
        log.info("Created attendance report preset {} for station {}", preset.id(), stationId);
        return preset;
    }

    /**
     * Deletes a report preset by its identifier.
     */
    public boolean deletePreset(int id) {
        if (attendanceRepository.deletePreset(id)) {
            log.info("Deleted attendance report preset {}", id);
            return true;
        }
        log.warn("Cannot delete attendance report preset: preset {} not found", id);
        return false;
    }

    private static <T> List<T> presetSelection(List<T> selection) {
        if (selection == null) return List.of();
        if (selection.stream().anyMatch(Objects::isNull)) {
            throw AttendanceRefusal.ATTENDANCE_REPORT_PRESET_EMPTY_ENTRY.raise();
        }
        return selection.stream().distinct().toList();
    }

    /**
     * Builds an attendance report with member summaries, session data, and monthly breakdowns.
     * Filters members by role or group and aggregates hours within the given time range.
     *
     * <p>A member who was at nothing in the period is left out rather than listed with a nought. The
     * monthly tables have always done this; the overall one used to list every member of the chosen
     * groups, so the same report contradicted itself on the same page and a reader scrolled past
     * empty rows to find the people who had actually been there. Preview and printout are built from
     * these same rows, so both say the same thing.
     */
    public ReportData buildReport(
            int stationId,
            List<StationUserType> userTypes,
            List<Integer> groupIds,
            Instant from,
            Instant to,
            String rounding) {
        ZoneId zone = resolveTimezone(stationId);
        Locale locale = resolveLocale(stationId);
        DateTimeFormatter monthFmt = DateTimeFormatter.ofPattern("MMMM yyyy", locale);
        var rawIdSet = new LinkedHashSet<Integer>();
        var filterLabels = new ArrayList<String>();
        for (var gid : groupIds) {
            rawIdSet.addAll(attendanceRepository.findMemberIdsByGroup(gid));
            var group = memberGroupRepository.findById(gid);
            filterLabels.add(group.map(MemberGroup::name).orElse("Gruppe #" + gid));
        }
        for (var ut : userTypes) {
            rawIdSet.addAll(attendanceRepository.findMemberIdsByUserType(stationId, ut));
            filterLabels.add(DocumentWord.forUserType(ut.name(), locale.getLanguage()));
        }
        String filterLabel = String.join(", ", filterLabels);
        var memberIds = Set.copyOf(rawIdSet);
        var sessions = attendanceRepository.findSessionsByStationInRange(stationId, from, to);
        var rulesBySession = timeRuleRepository.findSessionRules(
                sessions.stream().map(AttendanceSession::id).toList());
        var restrictionMembers = rulesBySession.isEmpty()
                ? Map.<Integer, RestrictionMember>of()
                : restrictionService.membersOf(memberIds);

        var memberNames = new LinkedHashMap<Integer, String>();
        var memberTotalHours = new LinkedHashMap<Integer, Double>();
        var memberPreparation = new LinkedHashMap<Integer, Integer>();
        var memberFollowUp = new LinkedHashMap<Integer, Integer>();
        var memberSessionCount = new LinkedHashMap<Integer, Integer>();
        var memberPresentCount = new LinkedHashMap<Integer, Integer>();

        var monthlyHours = new LinkedHashMap<YearMonth, Map<Integer, Double>>();
        var monthlySessions = new LinkedHashMap<YearMonth, Map<Integer, Integer>>();
        var monthlyPresent = new LinkedHashMap<YearMonth, Map<Integer, Integer>>();
        var monthlyPreparation = new LinkedHashMap<YearMonth, Map<Integer, Integer>>();
        var monthlyFollowUp = new LinkedHashMap<YearMonth, Map<Integer, Integer>>();
        var monthlySessionData = new LinkedHashMap<YearMonth, List<SessionData>>();

        var sessionDataList = new ArrayList<SessionData>();

        for (var session : sessions) {
            var allEntries = attendanceRepository.findEntries(session.id());
            var expectedEntries = allEntries.stream()
                    .filter(e -> e.source() == AttendanceEntry.EntrySource.EXPECTED)
                    .toList();
            int expectedCount = expectedEntries.size();
            int presentCount = (int) expectedEntries.stream()
                    .filter(e -> e.status() == AttendanceEntry.AttendanceStatus.PRESENT)
                    .count();
            var presentEntriesInFilter = allEntries.stream()
                    .filter(e -> memberIds.contains(e.memberId()))
                    .filter(e -> e.status() == AttendanceEntry.AttendanceStatus.PRESENT)
                    .toList();
            if (presentEntriesInFilter.isEmpty()) continue;

            YearMonth ym = session.startTime() != null
                    ? YearMonth.from(session.startTime().atZone(zone))
                    : null;

            var rules = rulesBySession.getOrDefault(session.id(), List.of());
            var entryDataList = new ArrayList<SessionMemberEntry>();
            for (var entry : presentEntriesInFilter) {
                String name = resolveMemberName(entry.memberId(), memberNames);
                Instant checkIn = entry.shownCheckIn(session.startTime());
                Instant checkOut = entry.shownCheckOut(session.endTime());
                ExtraTime extra = checkIn == null || checkOut == null
                        ? ExtraTime.NONE
                        : extraTimeOf(entry, rules, restrictionMembers);
                double hours = computeHours(entry, checkIn, checkOut, rounding, session, extra);

                entryDataList.add(new SessionMemberEntry(
                        entry.memberId(),
                        name,
                        entry.status(),
                        checkIn != null ? LocalDateTime.ofInstant(checkIn, zone) : null,
                        checkOut != null ? LocalDateTime.ofInstant(checkOut, zone) : null,
                        hours,
                        extra.preparationMinutes(),
                        extra.followUpMinutes()));

                memberTotalHours.merge(entry.memberId(), hours, Double::sum);
                memberPreparation.merge(entry.memberId(), extra.preparationMinutes(), Integer::sum);
                memberFollowUp.merge(entry.memberId(), extra.followUpMinutes(), Integer::sum);
                memberSessionCount.merge(entry.memberId(), 1, Integer::sum);
                if (entry.status() == AttendanceEntry.AttendanceStatus.PRESENT) {
                    memberPresentCount.merge(entry.memberId(), 1, Integer::sum);
                }

                if (ym != null) {
                    monthlyHours
                            .computeIfAbsent(ym, _ -> new LinkedHashMap<>())
                            .merge(entry.memberId(), hours, Double::sum);
                    monthlySessions
                            .computeIfAbsent(ym, _ -> new LinkedHashMap<>())
                            .merge(entry.memberId(), 1, Integer::sum);
                    monthlyPreparation
                            .computeIfAbsent(ym, _ -> new LinkedHashMap<>())
                            .merge(entry.memberId(), extra.preparationMinutes(), Integer::sum);
                    monthlyFollowUp
                            .computeIfAbsent(ym, _ -> new LinkedHashMap<>())
                            .merge(entry.memberId(), extra.followUpMinutes(), Integer::sum);
                    if (entry.status() == AttendanceEntry.AttendanceStatus.PRESENT) {
                        monthlyPresent
                                .computeIfAbsent(ym, _ -> new LinkedHashMap<>())
                                .merge(entry.memberId(), 1, Integer::sum);
                    }
                }
            }

            Integer countedMinutes = session.countedMinutes();
            var sessionData = new SessionData(
                    session.id(),
                    Objects.requireNonNullElse(session.title(), ""),
                    session.startTime() != null
                            ? session.startTime().atZone(zone).toLocalDate()
                            : null,
                    session.endTime() != null ? session.endTime().atZone(zone).toLocalDate() : null,
                    session.startTime() != null
                            ? session.startTime().atZone(zone).toLocalTime()
                            : null,
                    session.endTime() != null ? session.endTime().atZone(zone).toLocalTime() : null,
                    expectedCount,
                    presentCount,
                    countedMinutes != null ? countedMinutes / 60.0 : null,
                    entryDataList);
            sessionDataList.add(sessionData);
            if (ym != null) {
                monthlySessionData.computeIfAbsent(ym, _ -> new ArrayList<>()).add(sessionData);
            }
        }

        var memberSummaries = new ArrayList<MemberSummary>();
        for (int memberId : memberIds) {
            double hours = memberTotalHours.getOrDefault(memberId, 0.0);
            int attended = memberSessionCount.getOrDefault(memberId, 0);
            if (hours == 0 && attended == 0) continue;
            memberSummaries.add(new MemberSummary(
                    memberId,
                    resolveMemberName(memberId, memberNames),
                    hours,
                    attended,
                    memberPresentCount.getOrDefault(memberId, 0),
                    memberPreparation.getOrDefault(memberId, 0),
                    memberFollowUp.getOrDefault(memberId, 0)));
        }
        memberSummaries.sort((a, b) -> a.name().compareToIgnoreCase(b.name()));

        var monthlySummaryList = new ArrayList<MonthSummary>();
        for (var ym : monthlyHours.keySet()) {
            var monthMembers = new ArrayList<MemberSummary>();
            var hoursMap = monthlyHours.get(ym);
            var sessMap = monthlySessions.getOrDefault(ym, Map.of());
            var presMap = monthlyPresent.getOrDefault(ym, Map.of());
            var prepMap = monthlyPreparation.getOrDefault(ym, Map.of());
            var followMap = monthlyFollowUp.getOrDefault(ym, Map.of());
            for (int memberId : memberIds) {
                double h = hoursMap.getOrDefault(memberId, 0.0);
                if (h == 0 && sessMap.getOrDefault(memberId, 0) == 0) continue;
                String name = resolveMemberName(memberId, memberNames);
                monthMembers.add(new MemberSummary(
                        memberId,
                        name,
                        h,
                        sessMap.getOrDefault(memberId, 0),
                        presMap.getOrDefault(memberId, 0),
                        prepMap.getOrDefault(memberId, 0),
                        followMap.getOrDefault(memberId, 0)));
            }
            monthMembers.sort((a, b) -> a.name().compareToIgnoreCase(b.name()));
            var monthSessions = monthlySessionData.getOrDefault(ym, List.of());
            monthlySummaryList.add(
                    new MonthSummary(monthFmt.format(ym.atDay(1).atStartOfDay(zone)), monthMembers, monthSessions));
        }

        return new ReportData(filterLabel, memberSummaries, sessionDataList, monthlySummaryList);
    }

    /**
     * Exports an attendance report as a PDF using Typst templates.
     * Includes station logo, member hours, session details, and monthly summaries.
     *
     * @return the PDF bytes, or empty if generation fails
     */
    public Optional<ExportedDocument> exportReportPdf(
            int stationId,
            List<StationUserType> userTypes,
            List<Integer> groupIds,
            Instant from,
            Instant to,
            String rounding,
            String generatedBy,
            String period) {
        var report = buildReport(stationId, userTypes, groupIds, from, to, rounding);
        ZoneId zone = resolveTimezone(stationId);
        var station = stationRepository.findById(stationId).orElse(null);
        String language = StationFormat.languageOf(station);

        var data = new LinkedHashMap<String, Object>();
        data.put("from", DATE_FMT.format(from.atZone(zone)));
        data.put("to", DATE_FMT.format(to.atZone(zone)));
        data.put("filterLabel", report.filterLabel());
        data.put("members", report.members().stream().map(this::memberToMap).toList());
        data.put(
                "monthlySummaries",
                report.monthlySummaries().stream()
                        .map(ms -> {
                            var m = new LinkedHashMap<String, Object>();
                            m.put("month", ms.month());
                            m.put(
                                    "members",
                                    ms.members().stream().map(this::memberToMap).toList());
                            m.put(
                                    "sessions",
                                    ms.sessions().stream()
                                            .map(session -> sessionToMap(session, language))
                                            .toList());
                            return m;
                        })
                        .toList());
        data.put(
                "sessions",
                report.sessions().stream()
                        .map(session -> sessionToMap(session, language))
                        .toList());

        data.put("stationName", station != null ? station.name() : "");
        data.put("generatedBy", generatedBy != null ? generatedBy : "");
        data.put("generatedAt", DATE_FMT.format(Instant.now().atZone(zone)));
        data.put("baseUrl", apiConfig.baseUrl());
        data.put("showInstanceUrl", StationFormat.showsInstanceUrl(station));
        data.put("hasLogo", false);

        try {
            var logo = logoService.original(stationId);
            String templateName = language + "/"
                    + ("year".equals(period) ? "attendance-report-year.typ" : "attendance-report-period.typ");
            String filename = reportFileName(period, from, zone, language, report.filterLabel());
            return Optional.of(new ExportedDocument(renderPdf(data, templateName, logo.orElse(null)), filename));
        } catch (Exception e) {
            log.error("Failed to export attendance report PDF", e);
            return Optional.empty();
        }
    }

    /**
     * The same summary as a spreadsheet, for a reader who has to add the hours up somewhere else.
     *
     * <p>The rows are the ones the preview shows and the printout prints, so the three cannot
     * disagree: a member left out of one is left out of all of them. The per-session detail below the
     * summary is deliberately not here, because what this is for is the total against a name. The
     * preparation and follow-up counted into that total stand beside it in minutes, so a reader can
     * tell the time spent at appointments from the time spent around them.
     */
    public Optional<ExportedDocument> exportReportCsv(
            int stationId,
            List<StationUserType> userTypes,
            List<Integer> groupIds,
            Instant from,
            Instant to,
            String rounding,
            String period,
            CsvWriter.Separator separator) {
        var report = buildReport(stationId, userTypes, groupIds, from, to, rounding);
        var station = stationRepository.findById(stationId).orElse(null);
        String locale = StationFormat.languageOf(station);
        ZoneId zone = resolveTimezone(stationId);

        boolean english = "en".equals(locale);
        var headers = List.of(
                DocumentWord.MEMBERS.in(locale),
                english ? "Sessions" : "Termine",
                english ? "Hours" : "Stunden",
                english ? "Preparation (min)" : "Vorbereitung (Min.)",
                english ? "Follow-up (min)" : "Nachbereitung (Min.)");
        var hoursFormat = hoursFormatFor(locale);
        var rows = report.members().stream()
                .map(member -> List.of(
                        member.name(),
                        String.valueOf(member.sessionCount()),
                        hoursFormat.format(member.totalHours()),
                        String.valueOf(member.preparationMinutes()),
                        String.valueOf(member.followUpMinutes())))
                .toList();

        String filename =
                reportFileName(period, from, zone, locale, report.filterLabel()).replaceAll("\\.pdf$", ".csv");
        return Optional.of(ExportedDocument.ofText(CsvWriter.write(headers, rows, separator), filename));
    }

    /**
     * Hours are written the way the station's own language writes a number.
     *
     * <p>Seven and a half is {@code 7,5} in German and {@code 7.5} in English, and a spreadsheet
     * reads a number it does not recognise as text, which is the difference between a column that
     * adds up and one that does not.
     */
    private static DecimalFormat hoursFormatFor(String locale) {
        Locale numbers = "en".equals(locale) ? Locale.ENGLISH : Locale.GERMANY;
        return new DecimalFormat("0.0", DecimalFormatSymbols.getInstance(numbers));
    }

    /**
     * What a reader finds this report called in their downloads folder.
     *
     * <p>Reads {@code Anwesenheit - Jugend - Januar 2026.pdf}: what it is, whose it is, and when it
     * covers. The filter goes in only where it names one thing, because a report covering several
     * groups is about all of them and a name carrying only the first would say something the document
     * does not.
     */
    static String reportFileName(String period, Instant from, ZoneId zone, String locale, String filterLabel) {
        String oneFilter = filterLabel != null && !filterLabel.contains(",") ? filterLabel : "";
        return DocumentName.of(
                "pdf",
                DocumentWord.ATTENDANCE.in(locale),
                DocumentName.part(DocumentWord.forUserType(oneFilter, locale)),
                DocumentPeriod.of(period, from, zone, locale));
    }

    /**
     * What one entry adds to the hours, rounded the way the report was asked for.
     *
     * <p>How long the presence is worth is the sheet's to say: a sheet with a number of its own
     * counts a whole presence as that number and a part of one as its share, and a sheet without
     * counts the clock. Preparation and follow-up come on top in full, also on a sheet with a number
     * of its own, since that number says what the appointment is worth and not the work around it.
     * The rounding comes last, over the whole duty, so the printed number is the one that was added
     * up.
     */
    private double computeHours(
            AttendanceEntry entry,
            @Nullable Instant checkIn,
            @Nullable Instant checkOut,
            String rounding,
            AttendanceSession session,
            ExtraTime extra) {
        if (entry.status() != AttendanceEntry.AttendanceStatus.PRESENT || checkIn == null || checkOut == null) return 0;
        double hours = session.countedHours(checkIn, checkOut) + extra.totalMinutes() / 60.0;
        return switch (rounding) {
            case "ceil" -> Math.ceil(hours);
            case "round" -> Math.round(hours * 2) / 2.0;
            default -> Math.round(hours * 100) / 100.0;
        };
    }

    /**
     * One sheet as the report template reads it.
     *
     * <p>A sheet that runs into another day says so: its own line carries the second date, and every
     * arrival and departure under it carries the day it happened on, since a time on its own would
     * be two different moments at once. A sheet counted by hand says that too, so that a reader
     * adding the column up can see why it does not follow the times printed beside it.
     */
    private Map<String, Object> sessionToMap(SessionData s, String language) {
        boolean spansDays = s.endDate() != null && !s.endDate().equals(s.date());
        var m = new LinkedHashMap<String, Object>();
        m.put("title", s.title());
        m.put("expectedCount", s.expectedCount());
        m.put("presentCount", s.presentCount());
        m.put("date", s.date());
        m.put("endDate", spansDays ? s.endDate() : "");
        m.put("startTime", s.startTime());
        m.put("endTime", s.endTime());
        m.put("countedHours", s.countedHours() != null ? String.format("%.1f", s.countedHours()) : "");
        m.put(
                "entries",
                s.entries().stream()
                        .map(e -> {
                            var em = new LinkedHashMap<String, Object>();
                            em.put("name", e.name());
                            em.put("status", e.status());
                            em.put("checkIn", momentInSheet(e.checkIn(), spansDays));
                            em.put("checkOut", momentInSheet(e.checkOut(), spansDays));
                            em.put("hours", String.format("%.1f", e.hours()));
                            em.put("extra", extraTimeText(e.preparationMinutes(), e.followUpMinutes(), language));
                            return em;
                        })
                        .toList());
        return m;
    }

    /**
     * The preparation and follow-up counted for one person, as the line beside their name reads it,
     * empty where there was none. Only the parts that count are named.
     */
    static String extraTimeText(int preparationMinutes, int followUpMinutes, String language) {
        boolean english = "en".equals(language);
        var parts = new ArrayList<String>();
        if (preparationMinutes > 0) {
            parts.add(
                    english
                            ? "+" + preparationMinutes + " min preparation"
                            : "+" + preparationMinutes + " Min. Vorbereitung");
        }
        if (followUpMinutes > 0) {
            parts.add(
                    english ? "+" + followUpMinutes + " min follow-up" : "+" + followUpMinutes + " Min. Nachbereitung");
        }
        return String.join(", ", parts);
    }

    /**
     * A moment as it is printed inside a sheet: a time, with the day in front of it where the sheet
     * runs over more than one.
     */
    private String momentInSheet(LocalDateTime moment, boolean spansDays) {
        if (moment == null) return "";
        return spansDays
                ? SHEET_DAY_FMT.format(moment) + " " + SHEET_TIME_FMT.format(moment)
                : SHEET_TIME_FMT.format(moment);
    }

    private Map<String, Object> memberToMap(MemberSummary m) {
        var map = new LinkedHashMap<String, Object>();
        map.put("name", m.name());
        map.put("totalHours", String.format("%.1f", m.totalHours()));
        map.put("sessionCount", m.sessionCount());
        map.put("presentCount", m.presentCount());
        return map;
    }

    /**
     * The preparation and follow-up one present person is credited with on one sheet: nothing where
     * the sheet says they did not do it, otherwise what the sheet's rules give them.
     */
    private static ExtraTime extraTimeOf(
            AttendanceEntry entry, List<TimeRule> rules, Map<Integer, RestrictionMember> members) {
        if (entry.skipExtraTime() || rules.isEmpty()) return ExtraTime.NONE;
        RestrictionMember member = members.get(entry.memberId());
        return member == null ? ExtraTime.NONE : ExtraTime.of(rules, member);
    }

    private String resolveMemberName(int memberId, Map<Integer, String> cache) {
        return cache.computeIfAbsent(memberId, id -> {
            var member = stationMemberRepository.findById(id);
            if (member.isEmpty()) return "#" + id;
            Integer accountId = member.get().accountId();
            if (accountId == null) return "#" + id;
            var account = accountRepository.findById(accountId);
            if (account.isEmpty()) return "#" + id;
            Account acc = account.get();
            String name = NameParts.of(acc).official();
            return name.isEmpty() ? acc.email() : name;
        });
    }

    private Locale resolveLocale(int stationId) {
        return StationFormat.localeOf(stationRepository.findById(stationId).orElse(null));
    }

    private ZoneId resolveTimezone(int stationId) {
        return StationFormat.timezoneOf(stationRepository.findById(stationId).orElse(null));
    }

    private byte[] renderPdf(Map<String, Object> data, String templateName, MediaContent logo)
            throws IOException, InterruptedException {
        return TypstCompiler.compileTemplate(
                data,
                templateName,
                logo != null ? new TypstCompiler.StationLogo(logo.data(), logo.contentType()) : null);
    }

    /**
     * Aggregated attendance report with filter label, members, sessions, and monthly breakdowns.
     */
    public record ReportData(
            String filterLabel,
            List<MemberSummary> members,
            List<SessionData> sessions,
            List<MonthSummary> monthlySummaries) {}

    /**
     * Summary of a member's attendance across all sessions in the report period.
     *
     * @param totalHours         the hours counted, preparation and follow-up included
     * @param preparationMinutes the preparation counted into them
     * @param followUpMinutes    the follow-up counted into them
     */
    public record MemberSummary(
            int memberId,
            String name,
            double totalHours,
            int sessionCount,
            int presentCount,
            int preparationMinutes,
            int followUpMinutes) {}

    /**
     * Data for a single attendance session including per-member entries.
     *
     * @param date         the day it starts on
     * @param endDate      the day it ends on, which is the same day for all but a camp
     * @param countedHours what a whole presence there was worth, null where its times decided
     */
    public record SessionData(
            int sessionId,
            String title,
            LocalDate date,
            LocalDate endDate,
            LocalTime startTime,
            LocalTime endTime,
            int expectedCount,
            int presentCount,
            @Nullable Double countedHours,
            List<SessionMemberEntry> entries) {}

    /**
     * A member's attendance entry within a specific session.
     *
     * <p>The two moments carry their day, because a sheet that runs over a weekend cannot say when
     * somebody arrived with a time alone. Preparation and follow-up are not in the moments: they are
     * counted into the hours and named beside them, so the times still say when somebody was there.
     *
     * @param hours              the hours counted, preparation and follow-up included
     * @param preparationMinutes the preparation counted into them
     * @param followUpMinutes    the follow-up counted into them
     */
    public record SessionMemberEntry(
            int memberId,
            String name,
            AttendanceEntry.AttendanceStatus status,
            @Nullable LocalDateTime checkIn,
            @Nullable LocalDateTime checkOut,
            double hours,
            int preparationMinutes,
            int followUpMinutes) {}

    /**
     * Monthly breakdown with per-member summaries and session details.
     */
    public record MonthSummary(String month, List<MemberSummary> members, List<SessionData> sessions) {}
}
