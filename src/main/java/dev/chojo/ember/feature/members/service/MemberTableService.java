/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.service;

import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.feature.members.entity.MemberTable;
import dev.chojo.ember.feature.members.entity.MemberTableColumn;
import dev.chojo.ember.feature.members.entity.MemberTableColumnKind;
import dev.chojo.ember.feature.members.entity.MemberTablePeople;
import dev.chojo.ember.feature.members.entity.ProfileField;
import dev.chojo.ember.feature.members.entity.ProfileFieldType;
import dev.chojo.ember.feature.members.entity.RichMember;
import dev.chojo.ember.feature.members.repository.ProfileFieldRepository;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.entity.StationFormat;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

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
            offered.add(
                    new MemberTable.MemberTableHeader(builtin.label, MemberTableColumnKind.BUILTIN, builtin.key, null));
        }
        for (var field : readableFields(stationId, permissions).values()) {
            offered.add(new MemberTable.MemberTableHeader(
                    field.name(), MemberTableColumnKind.PROFILE_FIELD, null, field.id()));
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
     * @param questionLabels what an appointment calls each of its own questions, by question id, and
     *                       empty where the table is not drawn for one
     * @return the table, carrying only the columns that survived
     */
    public MemberTable build(
            Station station,
            MemberTablePeople people,
            List<MemberTableColumn> requested,
            Set<StationPermission> permissions,
            Map<Integer, String> questionLabels) {
        var readable = readableFields(station.id(), permissions);
        var kept = new ArrayList<MemberTableColumn>();
        var headers = new ArrayList<MemberTable.MemberTableHeader>();
        for (var column : requested) {
            if (!column.isWellFormed()) continue;
            var label = labelOf(column, readable, questionLabels);
            if (label == null) continue;
            kept.add(column);
            headers.add(new MemberTable.MemberTableHeader(label, column.kind(), column.key(), column.fieldId()));
        }

        var members = membersById(station.id());
        var values = valuesOfChosenFields(kept, readable);
        var rows = new ArrayList<MemberTable.MemberTableRow>();
        for (var memberId : people.memberIds()) {
            var member = members.get(memberId);
            if (member == null) continue;
            var cells = new ArrayList<String>(kept.size());
            for (var column : kept) {
                cells.add(valueOf(column, member, people, values, readable, station));
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

    private String labelOf(
            MemberTableColumn column, Map<Integer, ProfileField> readable, Map<Integer, String> questionLabels) {
        return switch (column.kind()) {
            case BUILTIN -> Builtin.labelOf(column.key());
            case PROFILE_FIELD -> {
                var field = readable.get(column.fieldId());
                yield field == null ? null : field.name();
            }
            case REGISTRATION_FIELD -> questionLabels.get(column.fieldId());
        };
    }

    private Map<Integer, RichMember> membersById(int stationId) {
        var byId = new HashMap<Integer, RichMember>();
        for (var member : stationMemberRepository.findRichMembers(stationId, true)) {
            byId.put(member.id(), member);
        }
        return byId;
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
            if (column.kind() != MemberTableColumnKind.PROFILE_FIELD) continue;
            collectField(column.fieldId(), readable, wanted);
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
            answers.put(value.memberId(), plain(value.value()));
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
    private ProfileField ageSourceOf(ProfileField field, Map<Integer, ProfileField> readable) {
        if (field.fieldType() != ProfileFieldType.AGE) return null;
        var config = field.config();
        if (config == null) return null;
        if (config.sourceFieldId() != null) return readable.get(config.sourceFieldId());
        if (config.sourceField() == null) return null;
        return readable.values().stream()
                .filter(candidate -> config.sourceField().equals(candidate.name()))
                .findFirst()
                .orElse(null);
    }

    private String valueOf(
            MemberTableColumn column,
            RichMember member,
            MemberTablePeople people,
            Map<Integer, Map<Integer, String>> values,
            Map<Integer, ProfileField> readable,
            Station station) {
        return switch (column.kind()) {
            case BUILTIN -> Builtin.valueOf(column.key(), member, people);
            case PROFILE_FIELD -> profileValue(column.fieldId(), member.id(), values, readable, station);
            case REGISTRATION_FIELD -> people.answersOf(member.id()).getOrDefault(column.fieldId(), "");
        };
    }

    private String profileValue(
            int fieldId,
            int memberId,
            Map<Integer, Map<Integer, String>> values,
            Map<Integer, ProfileField> readable,
            Station station) {
        var field = readable.get(fieldId);
        if (field == null) return "";
        if (field.fieldType() == ProfileFieldType.AGE) {
            var source = ageSourceOf(field, readable);
            if (source == null) return "";
            var born = values.getOrDefault(source.id(), Map.of()).get(memberId);
            return ageOf(born, field.config() == null ? null : field.config().ageMode(), station);
        }
        var stored = values.getOrDefault(fieldId, Map.of()).get(memberId);
        if (stored == null || stored.isBlank()) return "";
        return switch (field.fieldType()) {
            case DATE, BIRTH_DATE -> day(stored);
            case BOOLEAN -> "true".equalsIgnoreCase(stored) ? "Ja" : "Nein";
            default -> stored;
        };
    }

    /**
     * How old somebody is, counted the way the question says to count it.
     *
     * <p>A station that asks how old its people are on the last day of the year is asking who turns
     * old enough this year, which is a different question from who is old enough today.
     */
    private String ageOf(String born, String ageMode, Station station) {
        if (born == null || born.isBlank()) return "";
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
     * An answer as somebody wrote it, rather than as the database keeps it.
     *
     * <p>Answers are held as JSON, so a written answer comes back wearing its quotation marks and a
     * sheet printed straight from them would show every name in quotes.
     */
    private String plain(String stored) {
        if (stored == null) return null;
        var trimmed = stored.strip();
        if (trimmed.length() >= 2 && trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
            return trimmed.substring(1, trimmed.length() - 1).replace("\\\"", "\"");
        }
        if ("null".equals(trimmed)) return "";
        return trimmed;
    }

    private String day(String stored) {
        try {
            return LocalDate.parse(stored.length() > 10 ? stored.substring(0, 10) : stored)
                    .format(DAY);
        } catch (Exception e) {
            return stored;
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
        NAME("name", "Name"),
        MEMBER_TYPE("memberType", "Benutzertyp"),
        GROUPS("groups", "Gruppen"),
        TAGS("tags", "Tags"),
        EMAIL("email", "E-Mail"),
        JOIN_DATE("joinDate", "Eintritt"),
        REGISTRATION_STATUS("registrationStatus", "Anmeldung");

        private final String key;
        private final String label;

        Builtin(String key, String label) {
            this.key = key;
            this.label = label;
        }

        static String labelOf(String key) {
            for (var builtin : values()) {
                if (builtin.key.equals(key)) return builtin.label;
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
                    case JOIN_DATE ->
                        member.joinDate() == null ? "" : member.joinDate().format(DAY);
                    case REGISTRATION_STATUS -> people.registrationStatus().getOrDefault(member.id(), "");
                };
            }
            return "";
        }
    }
}
