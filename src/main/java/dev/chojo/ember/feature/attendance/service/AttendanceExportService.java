/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.attendance.service;

import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.conf.file.elements.Api;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.account.repository.AccountRepository;
import dev.chojo.ember.feature.attendance.entity.AttendanceEntry;
import dev.chojo.ember.feature.attendance.entity.AttendanceSession;
import dev.chojo.ember.feature.attendance.entity.AttendanceSessionField;
import dev.chojo.ember.feature.attendance.entity.AttendanceTemplate;
import dev.chojo.ember.feature.attendance.entity.AttendanceTemplateField;
import dev.chojo.ember.feature.attendance.entity.SessionAudience;
import dev.chojo.ember.feature.attendance.repository.AttendanceRepository;
import dev.chojo.ember.feature.media.entity.MediaContent;
import dev.chojo.ember.feature.members.entity.NameParts;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.repository.MemberGroupRepository;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import dev.chojo.ember.feature.question.QuestionText;
import dev.chojo.ember.feature.question.QuestionValues;
import dev.chojo.ember.feature.station.entity.StationFormat;
import dev.chojo.ember.feature.station.repository.StationRepository;
import dev.chojo.ember.feature.station.service.StationLogoService;
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
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

import static org.slf4j.LoggerFactory.getLogger;

/**
 * Service for exporting individual attendance sessions as PDF documents using Typst templates.
 */
@Singleton
public class AttendanceExportService {
    private static final Logger log = getLogger(AttendanceExportService.class);
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DAY_FMT = DateTimeFormatter.ofPattern("dd.MM.");
    private static final DateTimeFormatter DATE_TIME_FMT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");
    private final AttendanceRepository attendanceRepository;
    private final AccountRepository accountRepository;
    private final StationMemberRepository stationMemberRepository;
    private final MemberGroupRepository memberGroupRepository;
    private final StationRepository stationRepository;
    private final Api apiConfig;
    private final AttendanceAudienceService audienceService;
    private final StationLogoService logoService;

    @Inject
    public AttendanceExportService(
            AttendanceRepository attendanceRepository,
            AccountRepository accountRepository,
            StationMemberRepository stationMemberRepository,
            MemberGroupRepository memberGroupRepository,
            StationRepository stationRepository,
            Api apiConfig,
            AttendanceAudienceService audienceService,
            StationLogoService logoService) {
        this.audienceService = audienceService;
        this.logoService = logoService;
        this.attendanceRepository = attendanceRepository;
        this.accountRepository = accountRepository;
        this.stationMemberRepository = stationMemberRepository;
        this.memberGroupRepository = memberGroupRepository;
        this.stationRepository = stationRepository;
        this.apiConfig = apiConfig;
    }

    /**
     * What the sheet is printed as, beyond the attendance itself.
     *
     * @param signatureColumn whether every line ends in a box to sign, which turns the sheet into one
     *                        that is filled in on paper rather than one that reports what was recorded
     * @param title           the heading of the document, the session's own where this is null, and a
     *                        ruled line to write on where it is empty
     * @param blankRows       how many empty numbered lines follow the last person, for whoever turns
     *                        up without being on the list
     * @param showInstanceUrl whether the address of this installation is printed at the foot, which is
     *                        what the station settled on where this is null
     */
    public record SheetOptions(
            boolean signatureColumn,
            @Nullable String title,
            int blankRows,
            @Nullable Boolean showInstanceUrl) {
        /** The sheet as the product has always printed it. */
        public static final SheetOptions PLAIN = new SheetOptions(false, null, 0, null);

        /** At most as many blank lines as fit on a page beyond the people already on the sheet. */
        public static final int MAX_BLANK_ROWS = 40;

        public SheetOptions {
            blankRows = Math.clamp(blankRows, 0, MAX_BLANK_ROWS);
        }
    }

    public Optional<ExportedDocument> exportSessionPdf(int sessionId, String generatedBy) {
        return exportSessionPdf(sessionId, generatedBy, SheetOptions.PLAIN);
    }

    /**
     * What a sheet is called: the appointment it is for, and the day it falls on.
     *
     * <p>The title the reader typed for this export wins over the session's own, because it is the
     * more recent thing they said about what this sheet is. Where neither says anything, the day
     * carries the name on its own.
     */
    static String sheetFileName(AttendanceSession session, @Nullable String chosenTitle, ZoneId zone, String locale) {
        String title = chosenTitle != null && !chosenTitle.isBlank() ? chosenTitle : session.title();
        return DocumentName.of(
                "pdf",
                DocumentWord.ATTENDANCE_SHEET.in(locale),
                DocumentName.part(title),
                DocumentPeriod.day(session.startTime(), zone));
    }

    public Optional<ExportedDocument> exportSessionPdf(int sessionId, String generatedBy, SheetOptions options) {
        var session = attendanceRepository.findSessionById(sessionId);
        if (session.isEmpty()) return Optional.empty();

        var entries = attendanceRepository.findEntries(sessionId);
        var sessionFields = attendanceRepository.findSessionFields(sessionId);
        var templateFields = attendanceRepository.findSheetFields(sessionId);
        var audience = audienceService.audienceOf(session.get());

        var template = attendanceRepository.findTemplateById(session.get().templateId());
        int stationId = template.map(AttendanceTemplate::stationId).orElse(0);
        var station = stationRepository.findById(stationId).orElse(null);
        ZoneId zone = StationFormat.timezoneOf(station);

        String language = StationFormat.languageOf(station);
        var data = buildExportData(session.get(), entries, sessionFields, templateFields, zone, language);
        data.put(
                "sections",
                sections(audience, entries, stationId, language, entry -> buildEntryMap(entry, session.get(), zone)));
        data.put("stationName", station != null ? station.name() : "");
        data.put("generatedBy", generatedBy != null ? generatedBy : "");
        data.put("generatedAt", DATE_TIME_FMT.format(Instant.now().atZone(zone)));
        data.put("baseUrl", apiConfig.baseUrl());
        Boolean showInstanceUrl = options.showInstanceUrl();
        data.put(
                "showInstanceUrl", showInstanceUrl != null ? showInstanceUrl : StationFormat.showsInstanceUrl(station));
        data.put("hasLogo", false);
        data.put("signatureColumn", options.signatureColumn());
        data.put("blankRows", options.blankRows());
        String title = options.title();
        if (title != null) {
            data.put("title", title);
        }

        try {
            var logo = logoService.original(stationId);
            String locale = StationFormat.languageOf(station);
            String filename = sheetFileName(session.get(), title, zone, locale);
            return Optional.of(
                    new ExportedDocument(renderPdf(data, locale + "/attendance.typ", logo.orElse(null)), filename));
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
            ZoneId zone,
            String language) {
        var data = new LinkedHashMap<String, Object>();
        data.put("title", session.title() != null ? session.title() : "Anwesenheit");
        data.put("startTime", formatDateTime(session.startTime(), zone));
        data.put("endTime", formatDateTime(session.endTime(), zone));
        Integer countedMinutes = session.countedMinutes();
        data.put("countedHours", countedMinutes != null ? String.format("%.1f", countedMinutes / 60.0) : "");

        var values = new LinkedHashMap<Integer, String>();
        for (var sessionField : sessionFields) {
            values.put(sessionField.fieldId(), sessionField.value());
        }
        data.put("fields", fieldLines(templateFields, values, memberNames(templateFields, values), language));

        var allEntries = new ArrayList<StatusEntry>();
        for (var entry : entries) {
            allEntries.add(new StatusEntry(entry.status()));
        }
        data.put("entries", allEntries);

        return data;
    }

    /**
     * The sheet's lines, sectioned by whom the sheet expects.
     *
     * <p>A section per group comes first, in the sheet's order, as it always has, a member of two
     * groups standing in both. Then one per user type for whoever of that type no group has taken,
     * and last everybody else on the sheet. Only people with an entry are printed.
     *
     * @param audience  whom the sheet expects, its own or its template's
     * @param entries   the sheet's entries
     * @param stationId the station, whose members' user types decide the type sections
     * @param language  the station's language, which names the user types
     * @param line      how one entry is printed
     * @return the non-empty sections in print order
     */
    List<Section> sections(
            SessionAudience audience,
            List<AttendanceEntry> entries,
            int stationId,
            String language,
            Function<AttendanceEntry, Map<String, String>> line) {
        var entryByMember = new LinkedHashMap<Integer, AttendanceEntry>();
        for (var entry : entries) entryByMember.put(entry.memberId(), entry);

        var sections = new ArrayList<Section>();
        Set<Integer> assigned = new HashSet<>();
        for (int groupId : audience.groupIds()) {
            var group = memberGroupRepository.findById(groupId);
            if (group.isEmpty()) continue;
            var memberIds = memberGroupRepository.findMembers(groupId).stream()
                    .map(StationMember::id)
                    .toList();
            addSection(sections, group.get().name(), memberIds, entryByMember, assigned, line);
        }
        if (!audience.userTypes().isEmpty()) {
            var members = stationMemberRepository.findByStation(stationId);
            for (var type : StationUserType.values()) {
                if (!audience.userTypes().contains(type)) continue;
                var memberIds = members.stream()
                        .filter(member -> member.userType() == type && !assigned.contains(member.id()))
                        .map(StationMember::id)
                        .toList();
                addSection(
                        sections,
                        DocumentWord.forUserType(type.name(), language),
                        memberIds,
                        entryByMember,
                        assigned,
                        line);
            }
        }
        var rest = entryByMember.keySet().stream()
                .filter(memberId -> !assigned.contains(memberId))
                .toList();
        addSection(sections, "Sonstige", rest, entryByMember, assigned, line);
        return sections;
    }

    private static void addSection(
            List<Section> sections,
            String name,
            List<Integer> memberIds,
            Map<Integer, AttendanceEntry> entryByMember,
            Set<Integer> assigned,
            Function<AttendanceEntry, Map<String, String>> line) {
        var lines = new ArrayList<Map<String, String>>();
        for (int memberId : memberIds) {
            var entry = entryByMember.get(memberId);
            if (entry == null) continue;
            assigned.add(memberId);
            lines.add(line.apply(entry));
        }
        if (!lines.isEmpty()) sections.add(new Section(name, lines));
    }

    /**
     * One member's line on the exported sheet.
     *
     * <p>A sheet that runs over more than one day prints the day beside each moment, since a time
     * alone would name two of them.
     */
    private Map<String, String> buildEntryMap(AttendanceEntry entry, AttendanceSession session, ZoneId zone) {
        var map = new LinkedHashMap<String, String>();
        map.put("name", resolveMemberName(entry.memberId()));
        map.put("status", entry.status().name());
        Instant checkIn = entry.shownCheckIn(session.startTime());
        Instant checkOut = entry.shownCheckOut(session.endTime());
        boolean spansDays = session.spansDays(zone);
        map.put("checkIn", checkIn != null ? formatMoment(checkIn, zone, spansDays) : "");
        map.put("checkOut", checkOut != null ? formatMoment(checkOut, zone, spansDays) : "");
        return map;
    }

    private String resolveMemberName(int memberId) {
        var member = stationMemberRepository.findById(memberId);
        if (member.isEmpty()) return "#" + memberId;
        Integer accountId = member.get().accountId();
        if (accountId == null) return "#" + memberId;
        var account = accountRepository.findById(accountId);
        if (account.isEmpty()) return "#" + memberId;
        Account acc = account.get();
        String name = NameParts.of(acc).official();
        return name.isEmpty() ? acc.email() : name;
    }

    /**
     * The sheet's own answers as they are printed above the people, in the template's order.
     *
     * <p>Each answer is written the way every export writes one, so a yes reads as a word in the
     * station's language however it was stored. A field nobody filled in is left out rather than
     * printed empty.
     *
     * @param fields   the template's fields
     * @param values   the sheet's answers, by field id
     * @param names    the names of the members the answers name, by member id
     * @param language the station's language
     * @return one line per answered field
     */
    static List<NameValue> fieldLines(
            List<AttendanceTemplateField> fields,
            Map<Integer, String> values,
            Map<Integer, String> names,
            String language) {
        var lines = new ArrayList<NameValue>();
        for (var field : fields) {
            String text = QuestionText.format(field.fieldType(), values.get(field.id()), names, language);
            if (!text.isBlank()) lines.add(new NameValue(field.name(), text));
        }
        return lines;
    }

    /** The names of everybody the sheet's member fields name, written as the lines of people are. */
    private Map<Integer, String> memberNames(List<AttendanceTemplateField> fields, Map<Integer, String> values) {
        var names = new HashMap<Integer, String>();
        for (var field : fields) {
            if (!field.fieldType().namesMembers()) continue;
            for (int memberId : QuestionValues.memberIds(QuestionValues.read(values.get(field.id())))) {
                names.computeIfAbsent(memberId, this::resolveMemberName);
            }
        }
        return names;
    }

    private String formatMoment(Instant instant, ZoneId zone, boolean withDay) {
        var moment = instant.atZone(zone);
        return withDay ? DAY_FMT.format(moment) + " " + TIME_FMT.format(moment) : TIME_FMT.format(moment);
    }

    private String formatDateTime(Instant instant, ZoneId zone) {
        if (instant == null) return "";
        return DATE_TIME_FMT.format(instant.atZone(zone));
    }

    private byte[] renderPdf(Map<String, Object> data, String templateName, MediaContent logo)
            throws IOException, InterruptedException {
        return TypstCompiler.compileTemplate(
                data,
                templateName,
                logo != null ? new TypstCompiler.StationLogo(logo.data(), logo.contentType()) : null);
    }

    record NameValue(String name, String value) {}

    record Section(String name, List<Map<String, String>> entries) {}

    record StatusEntry(AttendanceEntry.AttendanceStatus status) {}
}
