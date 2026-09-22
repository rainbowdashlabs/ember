/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.form.service;

import dev.chojo.ember.feature.form.repository.FormRepository;
import dev.chojo.ember.feature.members.entity.MemberGroup;
import dev.chojo.ember.feature.members.entity.MemberTable;
import dev.chojo.ember.feature.members.entity.MemberTableCellType;
import dev.chojo.ember.feature.members.entity.MemberTableColumnKind;
import dev.chojo.ember.feature.members.repository.MemberGroupRepository;
import dev.chojo.ember.feature.members.service.MemberNameResolver;
import dev.chojo.ember.feature.members.service.MemberTableRenderer;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.entity.StationFormat;
import dev.chojo.ember.util.CsvWriter;
import dev.chojo.ember.util.DocumentName;
import dev.chojo.ember.util.DocumentPeriod;
import dev.chojo.ember.util.DocumentWord;
import dev.chojo.ember.util.ExportedDocument;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * The answers to a form, as a spreadsheet or as a sheet.
 *
 * <p>They could only be had as a spreadsheet before, and that spreadsheet was assembled in the
 * browser, which made it the one export with no server side at all. Both formats are drawn here now
 * from one set of columns, so a printed copy and a pasted one say the same thing.
 *
 * <p>The table is the same shape the member list and the registration list use, which is why this
 * needs no layout of its own: one question is one column, one submission is one row.
 */
@Singleton
public class FormResponseExportService {

    private static final DateTimeFormatter SUBMITTED_AT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final FormRepository formRepository;
    private final MemberNameResolver memberNameResolver;
    private final MemberTableRenderer renderer;
    private final FormRespondents respondents;
    private final MemberGroupRepository groups;

    @Inject
    public FormResponseExportService(
            FormRepository formRepository,
            MemberNameResolver memberNameResolver,
            MemberTableRenderer renderer,
            FormRespondents respondents,
            MemberGroupRepository groups) {
        this.formRepository = formRepository;
        this.memberNameResolver = memberNameResolver;
        this.renderer = renderer;
        this.respondents = respondents;
        this.groups = groups;
    }

    /**
     * Builds the answers as a document.
     *
     * @param separator what goes between the cells of a spreadsheet, or {@code null} for the sheet
     */
    public ExportedDocument export(
            int formId, String formTitle, Station station, String generatedBy, CsvWriter.Separator separator)
            throws Exception {
        var table = tableOf(formId, station);
        String language = StationFormat.languageOf(station);
        String filename = DocumentName.of(
                separator == null ? "pdf" : "csv",
                DocumentName.part(formTitle),
                DocumentWord.FORM_ANSWERS.in(language),
                DocumentPeriod.day(Instant.now(), StationFormat.timezoneOf(station)));

        if (separator != null) {
            return ExportedDocument.ofText(renderer.toCsv(table, station, separator), filename);
        }
        return new ExportedDocument(renderer.toPdf(table, station, formTitle, "", generatedBy), filename);
    }

    /**
     * One column per question, one row per submission, in the order the form asks and was answered.
     *
     * <p>Where members answered, their user type, groups and age on the day of answering follow the
     * name, so a spreadsheet can be pivoted by the same things the results view groups by. A form
     * answered only without signing in has nobody to describe and leaves those columns out.
     */
    private MemberTable tableOf(int formId, Station station) {
        String language = StationFormat.languageOf(station);
        var zone = StationFormat.timezoneOf(station);
        var questions = formRepository.findQuestions(formId);
        var responses = formRepository.findResponses(formId);
        boolean describesMembers = responses.stream().anyMatch(response -> response.memberId() != null);
        var described = describesMembers
                ? respondents.ofResponses(station.id(), responses)
                : List.<FormRespondents.Respondent>of();
        Map<Integer, String> groupNames = describesMembers
                ? groups.findByStation(station.id()).stream()
                        .collect(Collectors.toMap(MemberGroup::id, MemberGroup::name))
                : Map.of();

        var columns = new ArrayList<MemberTable.MemberTableHeader>();
        columns.add(column(DocumentWord.MEMBERS.in(language)));
        if (describesMembers) {
            columns.add(column(DocumentWord.MEMBER_TYPE.in(language)));
            columns.add(column(DocumentWord.GROUPS.in(language)));
            columns.add(column(DocumentWord.AGE.in(language)));
        }
        columns.add(column("en".equals(language) ? "Submitted" : "Abgegeben"));
        for (var question : questions) {
            columns.add(column(question.title()));
        }

        var rows = new ArrayList<MemberTable.MemberTableRow>(responses.size());
        for (int index = 0; index < responses.size(); index++) {
            var response = responses.get(index);
            var answers = new LinkedHashMap<Integer, String>();
            for (var answer : formRepository.findAnswers(response.id())) {
                answers.put(answer.questionId(), answer.value());
            }

            var values = new ArrayList<String>(columns.size());
            values.add(nameOf(response.memberId(), language));
            if (describesMembers) {
                var respondent = described.get(index);
                values.add(
                        respondent.userType() == null
                                ? ""
                                : DocumentWord.forUserType(respondent.userType().name(), language));
                values.add(respondent.groupIds().stream()
                        .map(groupNames::get)
                        .filter(Objects::nonNull)
                        .sorted()
                        .collect(Collectors.joining(", ")));
                values.add(respondent.age() == null ? "" : String.valueOf(respondent.age()));
            }
            values.add(
                    response.submittedAt() == null
                            ? ""
                            : SUBMITTED_AT.format(response.submittedAt().atZone(zone)));
            for (var question : questions) {
                values.add(answers.getOrDefault(question.id(), ""));
            }
            rows.add(new MemberTable.MemberTableRow(
                    response.memberId() == null ? 0 : response.memberId(), List.copyOf(values)));
        }
        return new MemberTable(List.copyOf(columns), List.copyOf(rows));
    }

    /** An answer given without an account behind it still has a row; it simply has nobody's name on it. */
    private String nameOf(Integer memberId, String language) {
        if (memberId == null) return "en".equals(language) ? "Not signed in" : "Ohne Anmeldung";
        String name = memberNameResolver.official(memberId);
        return name != null ? name : "#" + memberId;
    }

    private static MemberTable.MemberTableHeader column(String label) {
        return new MemberTable.MemberTableHeader(
                label, MemberTableColumnKind.BUILTIN, label, null, MemberTableCellType.TEXT);
    }
}
