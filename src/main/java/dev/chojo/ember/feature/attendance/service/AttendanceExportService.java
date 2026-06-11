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
import dev.chojo.ember.feature.attendance.entity.AttendanceFieldType;
import dev.chojo.ember.feature.attendance.entity.AttendanceSession;
import dev.chojo.ember.feature.attendance.entity.AttendanceSessionField;
import dev.chojo.ember.feature.attendance.entity.AttendanceTemplate;
import dev.chojo.ember.feature.attendance.entity.AttendanceTemplateField;
import dev.chojo.ember.feature.attendance.repository.AttendanceRepository;
import dev.chojo.ember.feature.attendance.repository.AttendanceRepository.TemplateGroup;
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
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.slf4j.LoggerFactory.getLogger;

/**
 * Service for exporting individual attendance sessions as PDF documents using Typst templates.
 */
@Singleton
public class AttendanceExportService {
    private static final Logger log = getLogger(AttendanceExportService.class);
    private static final ObjectMapper MAPPER = JsonMapper.builder().build();
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DATE_TIME_FMT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");
    private final AttendanceRepository attendanceRepository;
    private final AccountRepository accountRepository;
    private final StationMemberRepository stationMemberRepository;
    private final MemberGroupRepository memberGroupRepository;
    private final StationRepository stationRepository;
    private final Api apiConfig;

    @Inject
    public AttendanceExportService(
            AttendanceRepository attendanceRepository,
            AccountRepository accountRepository,
            StationMemberRepository stationMemberRepository,
            MemberGroupRepository memberGroupRepository,
            StationRepository stationRepository,
            Api apiConfig) {
        this.attendanceRepository = attendanceRepository;
        this.accountRepository = accountRepository;
        this.stationMemberRepository = stationMemberRepository;
        this.memberGroupRepository = memberGroupRepository;
        this.stationRepository = stationRepository;
        this.apiConfig = apiConfig;
    }

    public Optional<byte[]> exportSessionPdf(int sessionId, String generatedBy) {
        var session = attendanceRepository.findSessionById(sessionId);
        if (session.isEmpty()) return Optional.empty();

        var entries = attendanceRepository.findEntries(sessionId);
        var sessionFields = attendanceRepository.findSessionFields(sessionId);
        var templateFields =
                attendanceRepository.findTemplateFields(session.get().templateId());
        var templateGroups =
                attendanceRepository.findTemplateGroups(session.get().templateId());

        // Resolve station from the template
        var template = attendanceRepository.findTemplateById(session.get().templateId());
        int stationId = template.map(AttendanceTemplate::stationId).orElse(0);
        var station = stationRepository.findById(stationId).orElse(null);
        ZoneId zone = ZoneOffset.UTC;
        if (station != null && station.timezone() != null) {
            try {
                zone = ZoneId.of(station.timezone());
            } catch (Exception ignored) {
            }
        }

        var data = buildExportData(session.get(), entries, sessionFields, templateFields, templateGroups, zone);
        data.put("stationName", station != null ? station.name() : "");
        data.put("generatedBy", generatedBy != null ? generatedBy : "");
        data.put("generatedAt", DATE_TIME_FMT.format(Instant.now().atZone(zone)));
        data.put("baseUrl", apiConfig.baseUrl());
        data.put("hasLogo", false);

        try {
            var logo = stationRepository.findLogo(stationId);
            String locale = resolveLocalePrefix(station);
            return Optional.of(renderPdf(data, locale + "/attendance.typ", logo.orElse(null)));
        } catch (Exception e) {
            log.error("Failed to export attendance PDF for session {}", sessionId, e);
            return Optional.empty();
        }
    }

    private Map<String, Object> buildExportData(
            AttendanceSession session,
            List<AttendanceEntry> entries,
            List<AttendanceSessionField> sessionFields,
            List<AttendanceTemplateField> templateFields,
            List<TemplateGroup> templateGroups,
            ZoneId zone) {
        var data = new LinkedHashMap<String, Object>();
        data.put("title", session.title() != null ? session.title() : "Anwesenheit");
        data.put("startTime", formatDateTime(session.startTime(), zone));
        data.put("endTime", formatDateTime(session.endTime(), zone));

        // Build field name→value map (skip member/attendance fields)
        var fieldMap = new LinkedHashMap<Integer, String>();
        for (var sf : sessionFields) {
            fieldMap.put(sf.fieldId(), sf.value());
        }

        var fieldList = new ArrayList<NameValue>();
        for (var tf : templateFields) {
            String rawValue = fieldMap.get(tf.id());
            if (rawValue == null || rawValue.isBlank()) continue;
            String displayValue;
            if (isMemberField(tf.fieldType())) {
                displayValue = resolveMemberFieldValue(rawValue);
            } else {
                displayValue = formatFieldValue(rawValue);
            }
            if (displayValue.isBlank()) continue;
            fieldList.add(new NameValue(tf.name(), displayValue));
        }
        data.put("fields", fieldList);

        // Build entry lookup by memberId
        var entryByMember = new LinkedHashMap<Integer, AttendanceEntry>();
        for (var entry : entries) {
            entryByMember.put(entry.memberId(), entry);
        }

        // Build grouped sections
        var sections = new ArrayList<Section>();
        Set<Integer> assignedMemberIds = new HashSet<>();

        for (var tg : templateGroups) {
            var group = memberGroupRepository.findById(tg.groupId());
            if (group.isEmpty()) continue;
            var groupMembers = memberGroupRepository.findMembers(tg.groupId());
            var sectionEntries = new ArrayList<Map<String, String>>();
            for (var member : groupMembers) {
                var entry = entryByMember.get(member.id());
                if (entry == null) continue;
                sectionEntries.add(buildEntryMap(entry, session, zone));
                assignedMemberIds.add(member.id());
            }
            if (!sectionEntries.isEmpty()) {
                sections.add(new Section(group.get().name(), sectionEntries));
            }
        }

        // Ungrouped members
        var ungroupedEntries = new ArrayList<Map<String, String>>();
        for (var entry : entries) {
            if (!assignedMemberIds.contains(entry.memberId())) {
                ungroupedEntries.add(buildEntryMap(entry, session, zone));
            }
        }
        if (!ungroupedEntries.isEmpty()) {
            sections.add(new Section("Sonstige", ungroupedEntries));
        }

        data.put("sections", sections);

        // Flat entries list for summary counts
        var allEntries = new ArrayList<StatusEntry>();
        for (var entry : entries) {
            allEntries.add(new StatusEntry(entry.status().name()));
        }
        data.put("entries", allEntries);

        return data;
    }

    private Map<String, String> buildEntryMap(AttendanceEntry entry, AttendanceSession session, ZoneId zone) {
        var map = new LinkedHashMap<String, String>();
        map.put("name", resolveMemberName(entry.memberId()));
        map.put("status", entry.status().name());
        Instant checkIn = entry.checkIn() != null ? entry.checkIn() : session.startTime();
        Instant checkOut = entry.checkOut() != null ? entry.checkOut() : session.endTime();
        map.put("checkIn", checkIn != null ? formatTime(checkIn, zone) : "");
        map.put("checkOut", checkOut != null ? formatTime(checkOut, zone) : "");
        return map;
    }

    private String resolveMemberName(int memberId) {
        var member = stationMemberRepository.findById(memberId);
        if (member.isEmpty()) return "#" + memberId;
        var account = accountRepository.findById(member.get().accountId());
        if (account.isEmpty()) return "#" + memberId;
        Account acc = account.get();
        String name = (acc.firstName() + " " + acc.lastName()).trim();
        return name.isEmpty() ? acc.email() : name;
    }

    private boolean isMemberField(AttendanceFieldType fieldType) {
        return fieldType == AttendanceFieldType.MEMBER
                || fieldType == AttendanceFieldType.MEMBER_LIST
                || fieldType == AttendanceFieldType.MEMBER_OF_GROUP
                || fieldType == AttendanceFieldType.MEMBER_LIST_OF_GROUP;
    }

    private String resolveMemberFieldValue(String rawValue) {
        var ids = parseMemberIds(rawValue);
        if (ids.isEmpty()) return "";
        return ids.stream().map(this::resolveMemberName).collect(Collectors.joining(", "));
    }

    private List<Integer> parseMemberIds(String value) {
        var ids = new ArrayList<Integer>();
        if (value == null || value.isBlank()) return ids;
        String cleaned = value.trim();
        try {
            if (cleaned.startsWith("[")) {
                cleaned = cleaned.replaceAll("[\\[\\]\"\\s]", "");
                for (String part : cleaned.split(",")) {
                    if (!part.isBlank()) ids.add(Integer.parseInt(part.trim()));
                }
            } else {
                cleaned = cleaned.replace("\"", "").trim();
                if (!cleaned.isBlank()) ids.add(Integer.parseInt(cleaned));
            }
        } catch (NumberFormatException ignored) {
        }
        return ids;
    }

    private String formatFieldValue(String rawValue) {
        if (rawValue == null) return "";
        String val = rawValue.trim();
        if (val.startsWith("\"") && val.endsWith("\"")) {
            val = val.substring(1, val.length() - 1);
        }
        return val;
    }

    private String resolveLocalePrefix(Station station) {
        if (station != null && station.locale() != null && station.locale().startsWith("de")) return "de";
        return "en";
    }

    private String formatTime(Instant instant, ZoneId zone) {
        return TIME_FMT.format(instant.atZone(zone));
    }

    private String formatDateTime(Instant instant, ZoneId zone) {
        if (instant == null) return "";
        return DATE_TIME_FMT.format(instant.atZone(zone));
    }

    private byte[] renderPdf(Map<String, Object> data, String templateName, StationLogo logo)
            throws IOException, InterruptedException {
        return TypstCompiler.compileTemplate(
                data,
                templateName,
                logo != null ? new TypstCompiler.StationLogo(logo.data(), logo.contentType()) : null,
                MAPPER);
    }

    record NameValue(String name, String value) {}

    record Section(String name, List<Map<String, String>> entries) {}

    record StatusEntry(String status) {}
}
