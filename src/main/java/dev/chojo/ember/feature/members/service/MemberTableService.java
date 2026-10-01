/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.service;

import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.feature.members.entity.MemberTable;
import dev.chojo.ember.feature.members.entity.MemberTableCellType;
import dev.chojo.ember.feature.members.entity.MemberTableColumn;
import dev.chojo.ember.feature.members.entity.MemberTableColumnKind;
import dev.chojo.ember.feature.members.entity.MemberTablePeople;
import dev.chojo.ember.feature.members.entity.MemberTableQuestion;
import dev.chojo.ember.feature.members.entity.ProfileField;
import dev.chojo.ember.feature.members.entity.ProfileFieldType;
import dev.chojo.ember.feature.members.entity.RichMember;
import dev.chojo.ember.feature.members.repository.ProfileFieldRepository;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import dev.chojo.ember.feature.question.FieldType;
import dev.chojo.ember.feature.question.QuestionText;
import dev.chojo.ember.feature.question.QuestionValues;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.entity.StationFormat;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.time.LocalDate;
import java.time.Period;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Draws a table of people with the columns a station chose, cut to what the reader may see.
 *
 * <p>One table used twice. Who is coming to an appointment and who is on the register are the same
 * list of people asked for different reasons, and building them apart would give two column pickers,
 * two opinions about who may read an address, and two places to fix when a question is added.
 *
 * <p>The cutting happens here and nowhere else. A caller says which people and which columns; what
 * the reader is actually allowed to read is worked out from their own permissions through
 * {@link ProfileFieldScopes}, and a column they may not read is dropped rather than emptied. A column
 * of blanks tells a room that something was withheld about these people in particular, which is worse
 * than not offering the column at all.
 */
@Singleton
public class MemberTableService {
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private final ProfileFieldRepository profileFieldRepository;
    private final StationMemberRepository stationMemberRepository;

    @Inject
    public MemberTableService(
            ProfileFieldRepository profileFieldRepository, StationMemberRepository stationMemberRepository) {
        this.profileFieldRepository = profileFieldRepository;
        this.stationMemberRepository = stationMemberRepository;
    }

    /**
     * The columns a reader may choose from, which is what a picker is filled with.
     *
     * <p>Offered and drawn come from the same place on purpose: a picker that offered more than the
     * table will draw teaches a station to expect columns that never arrive.
     *
     * @param stationId   the station whose questions these are
     * @param permissions the reader's own permissions, already expanded
     * @return the builtin columns and the profile questions this reader may read
     */
    public List<MemberTable.MemberTableHeader> offerableColumns(int stationId, Set<StationPermission> permissions) {
        var offered = new ArrayList<MemberTable.MemberTableHeader>();
        for (var builtin : Builtin.values()) {
            offered.add(new MemberTable.MemberTableHeader(
                    builtin.label, MemberTableColumnKind.BUILTIN, builtin.key, null, builtin.type));
        }
        for (var field : readableFields(stationId, permissions).values()) {
            offered.add(new MemberTable.MemberTableHeader(
                    field.name(),
                    MemberTableColumnKind.PROFILE_FIELD,
                    null,
                    field.id(),
                    MemberTableCellType.of(field.fieldType().fieldType())));
        }
        return offered;
    }

    /**
     * Draws the table.
     *
     * @param station        the station the people belong to
     * @param people         who the table is about, and what the naming screen knows beyond that
     * @param requested      the columns asked for, which may name more than this reader may read
     * @param permissions    the reader's own permissions, already expanded
     * @param questions      an appointment's own questions, by question id, and empty where the table
     *                       is not drawn for one
     * @return the table, carrying only the columns that survived
     */
    public MemberTable build(
            Station station,
            MemberTablePeople people,
            List<MemberTableColumn> requested,
            Set<StationPermission> permissions,
            Map<Integer, MemberTableQuestion> questions) {
        var readable = readableFields(station.id(), permissions);
        var kept = new ArrayList<MemberTableColumn>();
        var headers = new ArrayList<MemberTable.MemberTableHeader>();
        for (var column : requested) {
            if (!column.isWellFormed()) continue;
            var header = headerOf(column, readable, questions);
            if (header == null) continue;
            kept.add(column);
            headers.add(header);
        }

        var members = membersById(station.id());
        var cellsOf =
                new Cells(people, valuesOfChosenFields(kept, readable), readable, questions, namesOf(members), station);
        var rows = new ArrayList<MemberTable.MemberTableRow>();
        for (var memberId : people.memberIds()) {
            var member = members.get(memberId);
            if (member == null) continue;
            var cells = new ArrayList<String>(kept.size());
            for (var column : kept) {
                cells.add(cellsOf.valueOf(column, member));
            }
            rows.add(new MemberTable.MemberTableRow(memberId, cells));
        }
        return new MemberTable(headers, rows);
    }

    /**
     * The questions of this station this reader may read, by id.
     *
     * <p>Everything else in here asks this first. A field absent from this map is one the reader may
     * not see, and there is deliberately no other way to reach a value.
     */
    private Map<Integer, ProfileField> readableFields(int stationId, Set<StationPermission> permissions) {
        var scopes = ProfileFieldScopes.readableBy(permissions);
        var byId = new LinkedHashMap<Integer, ProfileField>();
        for (var field : profileFieldRepository.findReadableBy(stationId, scopes)) {
            byId.put(field.id(), field);
        }
        return byId;
    }

    /** The header a requested column is drawn under, or null where this reader may not have it. */
    private MemberTable.@Nullable MemberTableHeader headerOf(
            MemberTableColumn column,
            Map<Integer, ProfileField> readable,
            Map<Integer, MemberTableQuestion> questions) {
        return switch (column.kind()) {
            case BUILTIN -> {
                var builtin = Builtin.of(column.key());
                yield builtin == null ? null : headerOf(column, builtin.label, builtin.type);
            }
            case PROFILE_FIELD -> {
                var field = readable.get(column.fieldId());
                yield field == null
                        ? null
                        : headerOf(
                                column,
                                field.name(),
                                MemberTableCellType.of(field.fieldType().fieldType()));
            }
            case REGISTRATION_FIELD -> {
                var question = questions.get(column.fieldId());
                yield question == null ? null : headerOf(column, question.label(), question.type());
            }
        };
    }

    private MemberTable.MemberTableHeader headerOf(MemberTableColumn column, String label, MemberTableCellType type) {
        return new MemberTable.MemberTableHeader(label, column.kind(), column.key(), column.fieldId(), type);
    }

    private Map<Integer, RichMember> membersById(int stationId) {
        var byId = new HashMap<Integer, RichMember>();
        for (var member : stationMemberRepository.findRichMembers(stationId, true)) {
            byId.put(member.id(), member);
        }
        return byId;
    }

    /** The name of every member of the station, which is whom an answer naming members can name. */
    private static Map<Integer, String> namesOf(Map<Integer, RichMember> members) {
        var names = new HashMap<Integer, String>();
        for (var member : members.values()) {
            String name = member.name();
            if (name != null && !name.isBlank()) names.put(member.id(), name);
        }
        return names;
    }

    /**
     * The answers to the chosen questions, read one question at a time.
     *
     * <p>Read this way rather than from the members themselves, which carry every answer they have
     * ever given: a value that was never fetched cannot be printed by mistake.
     */
    private Map<Integer, Map<Integer, String>> valuesOfChosenFields(
            List<MemberTableColumn> kept, Map<Integer, ProfileField> readable) {
        var wanted = new LinkedHashMap<Integer, Map<Integer, String>>();
        for (var column : kept) {
            Integer fieldId = column.fieldId();
            if (column.kind() != MemberTableColumnKind.PROFILE_FIELD || fieldId == null) continue;
            collectField(fieldId, readable, wanted);
        }
        return wanted;
    }

    /**
     * One question's answers, and the answers of whatever it counts from.
     *
     * <p>An age is not stored anywhere: the question holds the day somebody was born and the age is
     * worked out wherever it is shown. So choosing an age column means reading the date behind it,
     * which the reader may only do because the age itself passed the scopes.
     */
    private void collectField(
            int fieldId, Map<Integer, ProfileField> readable, Map<Integer, Map<Integer, String>> into) {
        if (into.containsKey(fieldId)) return;
        var field = readable.get(fieldId);
        if (field == null) return;
        var answers = new HashMap<Integer, String>();
        for (var value : profileFieldRepository.findValuesOfField(fieldId)) {
            answers.put(value.memberId(), value.value());
        }
        into.put(fieldId, answers);
        var source = ageSourceOf(field, readable);
        if (source != null) collectField(source.id(), readable, into);
    }

    /**
     * The question an age counts from, pointed at by id where the field carries one.
     *
     * <p>The name is the older way of saying it and is still what an untouched field carries.
     * Renaming the question it counted from used to empty the age, which is why the id is preferred.
     */
    private static @Nullable ProfileField ageSourceOf(ProfileField field, Map<Integer, ProfileField> readable) {
        if (field.fieldType() != ProfileFieldType.AGE) return null;
        var config = field.config();
        if (config == null) return null;
        Integer sourceFieldId = config.sourceFieldId();
        if (sourceFieldId != null) return readable.get(sourceFieldId);
        String sourceField = config.sourceField();
        if (sourceField == null) return null;
        return readable.values().stream()
                .filter(candidate -> sourceField.equals(candidate.name()))
                .findFirst()
                .orElse(null);
    }

    /**
     * Everything one drawing of the table reads its cells from.
     *
     * <p>A stored answer is printed the way every export prints one: a yes as a word in the station's
     * language, a date as a day, members by name. An age is the exception, being counted rather than
     * read.
     *
     * @param people    who the table is about, with their registration answers
     * @param values    the stored answers to the chosen profile questions, by question and member
     * @param readable  the profile questions this reader may read, by id
     * @param questions the appointment's own questions, by id
     * @param names     the station's members by name, for answers naming members
     * @param station   the station, whose language and time zone the cells are written in
     */
    private record Cells(
            MemberTablePeople people,
            Map<Integer, Map<Integer, String>> values,
            Map<Integer, ProfileField> readable,
            Map<Integer, MemberTableQuestion> questions,
            Map<Integer, String> names,
            Station station) {

        String valueOf(MemberTableColumn column, RichMember member) {
            Integer fieldId = column.fieldId();
            if (column.kind() == MemberTableColumnKind.BUILTIN) return Builtin.valueOf(column.key(), member, people);
            if (fieldId == null) return "";
            return column.kind() == MemberTableColumnKind.PROFILE_FIELD
                    ? profileValue(fieldId, member.id())
                    : registrationAnswer(fieldId, member.id());
        }

        private String profileValue(int fieldId, int memberId) {
            var field = readable.get(fieldId);
            if (field == null) return "";
            if (field.fieldType() == ProfileFieldType.AGE) return ageAnswer(field, memberId);
            var stored = values.getOrDefault(fieldId, Map.of()).get(memberId);
            return printed(field.fieldType().fieldType(), stored);
        }

        private String registrationAnswer(int questionId, int memberId) {
            var question = questions.get(questionId);
            if (question == null) return "";
            return printed(question.fieldType(), people.answersOf(memberId).get(questionId));
        }

        private String ageAnswer(ProfileField field, int memberId) {
            var source = ageSourceOf(field, readable);
            if (source == null) return "";
            var born = values.getOrDefault(source.id(), Map.of()).get(memberId);
            var config = field.config();
            return ageOf(QuestionValues.read(born), config == null ? null : config.ageMode(), station);
        }

        private String printed(FieldType type, @Nullable String stored) {
            return QuestionText.format(type, stored, names, StationFormat.languageOf(station));
        }
    }

    /**
     * How old somebody is, counted the way the question says to count it.
     *
     * <p>A station that asks how old its people are on the last day of the year is asking who turns
     * old enough this year, which is a different question from who is old enough today.
     */
    private static String ageOf(String born, @Nullable String ageMode, Station station) {
        if (born.isBlank()) return "";
        try {
            var day = LocalDate.parse(born.length() > 10 ? born.substring(0, 10) : born);
            var today = LocalDate.now(StationFormat.timezoneOf(station));
            var on = "end_of_year".equals(ageMode) ? today.withMonth(12).withDayOfMonth(31) : today;
            if (on.isBefore(day)) return "";
            return String.valueOf(Period.between(day, on).getYears());
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * What a membership says about somebody without anybody having asked a question.
     *
     * <p>These are not guarded by the field scopes, because they are not the station's questions: a
     * reader who may see the person at all may see their name and what kind of member they are. The
     * door in front of the table is what decides whether they may see the person.
     */
    private enum Builtin {
        NAME("name", "Name", MemberTableCellType.TEXT),
        MEMBER_TYPE("memberType", "Benutzertyp", MemberTableCellType.ENUM),
        GROUPS("groups", "Gruppen", MemberTableCellType.TEXT),
        TAGS("tags", "Tags", MemberTableCellType.TEXT),
        EMAIL("email", "E-Mail", MemberTableCellType.TEXT),
        JOIN_DATE("joinDate", "Eintritt", MemberTableCellType.DATE),
        REGISTRATION_STATUS("registrationStatus", "Anmeldung", MemberTableCellType.ENUM);

        private final String key;
        private final String label;
        private final MemberTableCellType type;

        Builtin(String key, String label, MemberTableCellType type) {
            this.key = key;
            this.label = label;
            this.type = type;
        }

        static @Nullable Builtin of(String key) {
            for (var builtin : values()) {
                if (builtin.key.equals(key)) return builtin;
            }
            return null;
        }

        static String valueOf(String key, RichMember member, MemberTablePeople people) {
            for (var builtin : values()) {
                if (!builtin.key.equals(key)) continue;
                return switch (builtin) {
                    case NAME -> member.name() == null ? "" : member.name();
                    case MEMBER_TYPE ->
                        member.userType() == null ? "" : member.userType().name();
                    case GROUPS ->
                        member.groups().stream()
                                .map(RichMember.GroupEntry::name)
                                .reduce((a, b) -> a + ", " + b)
                                .orElse("");
                    case TAGS ->
                        member.tags().stream()
                                .map(RichMember.TagEntry::name)
                                .reduce((a, b) -> a + ", " + b)
                                .orElse("");
                    case EMAIL -> member.email() == null ? "" : member.email();
                    case JOIN_DATE -> member.joinDate().format(DAY);
                    case REGISTRATION_STATUS -> people.registrationStatus().getOrDefault(member.id(), "");
                };
            }
            return "";
        }
    }
}
