/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.attendance.service;

import dev.chojo.ember.conf.file.elements.Api;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.account.repository.AccountRepository;
import dev.chojo.ember.feature.attendance.entity.AttendanceEntry;
import dev.chojo.ember.feature.attendance.entity.AttendanceReportPreset;
import dev.chojo.ember.feature.attendance.repository.AttendanceRepository;
import dev.chojo.ember.feature.members.entity.MemberGroup;
import dev.chojo.ember.feature.members.repository.MemberGroupRepository;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.repository.StationRepository;
import dev.chojo.ember.feature.station.repository.StationRepository.StationLogo;
import dev.chojo.ember.util.TypstCompiler;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
    private static final ObjectMapper MAPPER = JsonMapper.builder().build();
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");
    private final AttendanceRepository attendanceRepository;
    private final StationMemberRepository stationMemberRepository;
    private final AccountRepository accountRepository;
    private final StationRepository stationRepository;
    private final MemberGroupRepository memberGroupRepository;
    private final Api apiConfig;

    @Inject
    public AttendanceReportService(
            AttendanceRepository attendanceRepository,
            StationMemberRepository stationMemberRepository,
            AccountRepository accountRepository,
            StationRepository stationRepository,
            MemberGroupRepository memberGroupRepository,
            Api apiConfig) {
        this.attendanceRepository = attendanceRepository;
        this.stationMemberRepository = stationMemberRepository;
        this.accountRepository = accountRepository;
        this.stationRepository = stationRepository;
        this.memberGroupRepository = memberGroupRepository;
        this.apiConfig = apiConfig;
    }

    // -- Records for API responses --

    /**
     * Retrieves all report presets for a station.
     */
    public List<AttendanceReportPreset> findPresets(int stationId) {
        return attendanceRepository.findPresets(stationId);
    }

    /**
     * Creates a new report preset with the given filter configuration.
     */
    public AttendanceReportPreset createPreset(
            int stationId, String name, String roleName, Integer groupId, String period, String rounding) {
        return attendanceRepository.createPreset(stationId, name, roleName, groupId, period, rounding);
    }

    /**
     * Deletes a report preset by its identifier.
     */
    public boolean deletePreset(int id) {
        return attendanceRepository.deletePreset(id);
    }

    /**
     * Builds an attendance report with member summaries, session data, and monthly breakdowns.
     * Filters members by role or group and aggregates hours within the given time range.
     */
    public ReportData buildReport(
            int stationId, String roleName, Integer groupId, Instant from, Instant to, String rounding) {
        ZoneId zone = resolveTimezone(stationId);
        Locale locale = resolveLocale(stationId);
        DateTimeFormatter monthFmt = DateTimeFormatter.ofPattern("MMMM yyyy", locale);
        List<Integer> rawIds;
        String filterLabel;
        if (groupId != null) {
            rawIds = attendanceRepository.findMemberIdsByGroup(groupId);
            var group = memberGroupRepository.findById(groupId);
            filterLabel = group.map(MemberGroup::name).orElse("Gruppe #" + groupId);
        } else if (roleName != null && !roleName.isBlank()) {
            rawIds = attendanceRepository.findMemberIdsByUserType(stationId, roleName);
            filterLabel = roleName;
        } else {
            rawIds = List.of();
            filterLabel = "";
        }
        var memberIds = Set.copyOf(rawIds);
        var sessions = attendanceRepository.findSessionsByStationInRange(stationId, from, to);

        var memberNames = new LinkedHashMap<Integer, String>();
        var memberTotalHours = new LinkedHashMap<Integer, Double>();
        var memberSessionCount = new LinkedHashMap<Integer, Integer>();
        var memberPresentCount = new LinkedHashMap<Integer, Integer>();

        // Monthly tracking
        var monthlyHours = new LinkedHashMap<YearMonth, Map<Integer, Double>>();
        var monthlySessions = new LinkedHashMap<YearMonth, Map<Integer, Integer>>();
        var monthlyPresent = new LinkedHashMap<YearMonth, Map<Integer, Integer>>();
        var monthlySessionData = new LinkedHashMap<YearMonth, List<SessionData>>();

        var sessionDataList = new ArrayList<SessionData>();

        for (var session : sessions) {
            var allEntries = attendanceRepository.findEntries(session.id());
            // Total session counts: how many expected members, and how many of those are present
            var expectedEntries = allEntries.stream()
                    .filter(e -> e.source() == AttendanceEntry.EntrySource.EXPECTED)
                    .toList();
            int expectedCount = expectedEntries.size();
            int presentCount = (int) expectedEntries.stream()
                    .filter(e -> e.status() == AttendanceEntry.AttendanceStatus.PRESENT)
                    .count();
            // Entries for the report: only PRESENT members matching the role/group filter
            var filteredEntries = allEntries.stream()
                    .filter(e -> memberIds.contains(e.memberId()))
                    .filter(e -> e.status() == AttendanceEntry.AttendanceStatus.PRESENT)
                    .toList();
            if (filteredEntries.isEmpty()) continue;

            YearMonth ym = session.startTime() != null
                    ? YearMonth.from(session.startTime().atZone(zone))
                    : null;

            var entryDataList = new ArrayList<SessionMemberEntry>();
            for (var entry : filteredEntries) {
                String name = resolveMemberName(entry.memberId(), memberNames);
                Instant checkIn = entry.checkIn() != null ? entry.checkIn() : session.startTime();
                Instant checkOut = entry.checkOut() != null ? entry.checkOut() : session.endTime();
                double hours = computeHours(entry, checkIn, checkOut, rounding);

                entryDataList.add(new SessionMemberEntry(
                        entry.memberId(),
                        name,
                        entry.status().name(),
                        checkIn != null ? TIME_FMT.format(checkIn.atZone(zone)) : "",
                        checkOut != null ? TIME_FMT.format(checkOut.atZone(zone)) : "",
                        hours));

                memberTotalHours.merge(entry.memberId(), hours, Double::sum);
                memberSessionCount.merge(entry.memberId(), 1, Integer::sum);
                if (entry.status() == AttendanceEntry.AttendanceStatus.PRESENT) {
                    memberPresentCount.merge(entry.memberId(), 1, Integer::sum);
                }

                if (ym != null) {
                    monthlyHours
                            .computeIfAbsent(ym, k -> new LinkedHashMap<>())
                            .merge(entry.memberId(), hours, Double::sum);
                    monthlySessions
                            .computeIfAbsent(ym, k -> new LinkedHashMap<>())
                            .merge(entry.memberId(), 1, Integer::sum);
                    if (entry.status() == AttendanceEntry.AttendanceStatus.PRESENT) {
                        monthlyPresent
                                .computeIfAbsent(ym, k -> new LinkedHashMap<>())
                                .merge(entry.memberId(), 1, Integer::sum);
                    }
                }
            }

            var sessionData = new SessionData(
                    session.id(),
                    session.title() != null ? session.title() : "",
                    session.startTime() != null
                            ? DATE_FMT.format(session.startTime().atZone(zone))
                            : "",
                    session.startTime() != null
                            ? TIME_FMT.format(session.startTime().atZone(zone))
                            : "",
                    session.endTime() != null
                            ? TIME_FMT.format(session.endTime().atZone(zone))
                            : "",
                    expectedCount,
                    presentCount,
                    entryDataList);
            sessionDataList.add(sessionData);
            if (ym != null) {
                monthlySessionData.computeIfAbsent(ym, k -> new ArrayList<>()).add(sessionData);
            }
        }

        // Build overall member summaries
        var memberSummaries = new ArrayList<MemberSummary>();
        for (int memberId : memberIds) {
            String name = resolveMemberName(memberId, memberNames);
            memberSummaries.add(new MemberSummary(
                    memberId,
                    name,
                    memberTotalHours.getOrDefault(memberId, 0.0),
                    memberSessionCount.getOrDefault(memberId, 0),
                    memberPresentCount.getOrDefault(memberId, 0)));
        }
        memberSummaries.sort((a, b) -> a.name().compareToIgnoreCase(b.name()));

        // Build monthly summaries
        var monthlySummaryList = new ArrayList<MonthSummary>();
        for (var ym : monthlyHours.keySet()) {
            var monthMembers = new ArrayList<MemberSummary>();
            var hoursMap = monthlyHours.get(ym);
            var sessMap = monthlySessions.getOrDefault(ym, Map.of());
            var presMap = monthlyPresent.getOrDefault(ym, Map.of());
            for (int memberId : memberIds) {
                double h = hoursMap.getOrDefault(memberId, 0.0);
                if (h == 0 && sessMap.getOrDefault(memberId, 0) == 0) continue;
                String name = resolveMemberName(memberId, memberNames);
                monthMembers.add(new MemberSummary(
                        memberId, name, h, sessMap.getOrDefault(memberId, 0), presMap.getOrDefault(memberId, 0)));
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
    public Optional<byte[]> exportReportPdf(
            int stationId,
            String roleName,
            Integer groupId,
            Instant from,
            Instant to,
            String rounding,
            String generatedBy,
            boolean isYearReport) {
        var report = buildReport(stationId, roleName, groupId, from, to, rounding);
        ZoneId zone = resolveTimezone(stationId);

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
                                            .map(this::sessionToMap)
                                            .toList());
                            return m;
                        })
                        .toList());
        data.put("sessions", report.sessions().stream().map(this::sessionToMap).toList());

        // Station info
        var station = stationRepository.findById(stationId).orElse(null);
        data.put("stationName", station != null ? station.name() : "");
        data.put("generatedBy", generatedBy != null ? generatedBy : "");
        data.put("generatedAt", DATE_FMT.format(Instant.now().atZone(zone)));
        data.put("baseUrl", apiConfig.baseUrl());
        data.put("hasLogo", false);

        try {
            var logo = stationRepository.findLogo(stationId);
            String locale = resolveLocalePrefix(station);
            String templateName =
                    locale + "/" + (isYearReport ? "attendance-report-year.typ" : "attendance-report-period.typ");
            return Optional.of(renderPdf(data, templateName, logo.orElse(null)));
        } catch (Exception e) {
            log.error("Failed to export attendance report PDF", e);
            return Optional.empty();
        }
    }

    // -- Presets --

    private double computeHours(AttendanceEntry entry, Instant checkIn, Instant checkOut, String rounding) {
        if (entry.status() != AttendanceEntry.AttendanceStatus.PRESENT || checkIn == null || checkOut == null) return 0;
        double hours = Duration.between(checkIn, checkOut).toMinutes() / 60.0;
        if (hours < 0) return 0;
        return switch (rounding) {
            case "ceil" -> Math.ceil(hours);
            case "round" -> Math.round(hours * 2) / 2.0; // round to nearest 0.5
            default -> Math.round(hours * 100) / 100.0; // exact, 2 decimal places
        };
    }

    private Map<String, Object> sessionToMap(SessionData s) {
        var m = new LinkedHashMap<String, Object>();
        m.put("title", s.title());
        m.put("expectedCount", s.expectedCount());
        m.put("presentCount", s.presentCount());
        m.put("date", s.date());
        m.put("startTime", s.startTime());
        m.put("endTime", s.endTime());
        m.put(
                "entries",
                s.entries().stream()
                        .map(e -> {
                            var em = new LinkedHashMap<String, Object>();
                            em.put("name", e.name());
                            em.put("status", e.status());
                            em.put("checkIn", e.checkIn());
                            em.put("checkOut", e.checkOut());
                            em.put("hours", String.format("%.1f", e.hours()));
                            return em;
                        })
                        .toList());
        return m;
    }

    private Map<String, Object> memberToMap(MemberSummary m) {
        var map = new LinkedHashMap<String, Object>();
        map.put("name", m.name());
        map.put("totalHours", String.format("%.1f", m.totalHours()));
        map.put("sessionCount", m.sessionCount());
        map.put("presentCount", m.presentCount());
        return map;
    }

    // -- Report Building --

    private String resolveMemberName(int memberId, Map<Integer, String> cache) {
        return cache.computeIfAbsent(memberId, id -> {
            var member = stationMemberRepository.findById(id);
            if (member.isEmpty()) return "#" + id;
            var account = accountRepository.findById(member.get().accountId());
            if (account.isEmpty()) return "#" + id;
            Account acc = account.get();
            String name = (acc.firstName() + " " + acc.lastName()).trim();
            return name.isEmpty() ? acc.email() : name;
        });
    }

    private String resolveLocalePrefix(Station station) {
        if (station != null && station.locale() != null && station.locale().startsWith("de")) return "de";
        return "en";
    }

    // -- PDF Export --

    private Locale resolveLocale(int stationId) {
        var station = stationRepository.findById(stationId);
        if (station.isPresent() && station.get().locale() != null) {
            try {
                return Locale.forLanguageTag(station.get().locale());
            } catch (Exception ignored) {
            }
        }
        return Locale.GERMAN;
    }

    private ZoneId resolveTimezone(int stationId) {
        var station = stationRepository.findById(stationId);
        if (station.isPresent() && station.get().timezone() != null) {
            try {
                return ZoneId.of(station.get().timezone());
            } catch (Exception ignored) {
            }
        }
        return ZoneOffset.UTC;
    }

    private byte[] renderPdf(Map<String, Object> data, String templateName, StationLogo logo)
            throws IOException, InterruptedException {
        return TypstCompiler.compileTemplate(
                data,
                templateName,
                logo != null ? new TypstCompiler.StationLogo(logo.data(), logo.contentType()) : null,
                MAPPER);
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
     */
    public record MemberSummary(int memberId, String name, double totalHours, int sessionCount, int presentCount) {}

    /**
     * Data for a single attendance session including per-member entries.
     */
    public record SessionData(
            int sessionId,
            String title,
            String date,
            String startTime,
            String endTime,
            int expectedCount,
            int presentCount,
            List<SessionMemberEntry> entries) {}

    /**
     * A member's attendance entry within a specific session.
     */
    public record SessionMemberEntry(
            int memberId, String name, String status, String checkIn, String checkOut, double hours) {}

    /**
     * Monthly breakdown with per-member summaries and session details.
     */
    public record MonthSummary(String month, List<MemberSummary> members, List<SessionData> sessions) {}
}
