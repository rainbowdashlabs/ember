/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;
import dev.chojo.ember.feature.question.FieldType;
import dev.chojo.ember.feature.question.Question;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * A question the organiser answers once for an appointment, or once per date of it, together with
 * the answer it carries.
 *
 * <p>What the server and its own screens work with. Partner stations are handed an {@link EventField},
 * which keeps the names they have always read.
 *
 * @param value                 the answer, in the one shape its type is stored in
 * @param attendanceFieldId     the field of the attendance sheet the answer fills in, or null
 * @param isPublic              whether readers outside the station and partner stations see it
 * @param selfRegistrationState whether the reader asking may put themselves into this member
 *                              question, or null where the question takes no self-registration or
 *                              nobody asked on a reader's behalf
 * @param readerInWholeGroup    whether the reader asking stands in this member field of an event
 *                              group for the whole group, on an appointment of it: stepping out there
 *                              then means missing this appointment only
 */
public record AppointmentField(
        int id,
        int eventId,
        String name,
        FieldType fieldType,
        EventQuestionSettings config,
        String value,
        int position,
        boolean overview,
        @Nullable Integer attendanceFieldId,
        boolean isPublic,
        @Nullable SelfRegistration selfRegistrationState,
        boolean readerInWholeGroup) {

    /**
     * A question as stored, not yet measured against any reader.
     */
    public AppointmentField(
            int id,
            int eventId,
            String name,
            FieldType fieldType,
            EventQuestionSettings config,
            String value,
            int position,
            boolean overview,
            @Nullable Integer attendanceFieldId,
            boolean isPublic) {
        this(id, eventId, name, fieldType, config, value, position, overview, attendanceFieldId, isPublic, null, false);
    }

    /**
     * Whether this field comes from the appointment's event group rather than from the appointment
     * itself. Such a field carries the negated id of the event group's field.
     *
     * @return true for a field of the event group shown on one of its parts
     */
    public boolean inherited() {
        return inherited(id);
    }

    /**
     * The event group's field this one comes from.
     *
     * @return the id of the event group's field
     * @throws IllegalStateException for a field of the appointment itself
     */
    public int groupFieldId() {
        return groupFieldId(id);
    }

    /**
     * Whether a field id, as the screens send it, names a field of the event group shown on a part.
     *
     * @param fieldId the id
     * @return true for a negated id of an event group's field
     */
    public static boolean inherited(int fieldId) {
        return fieldId < 0;
    }

    /**
     * The id a field of an event group carries where one of its parts shows it.
     *
     * @param groupFieldId the id of the event group's field
     * @return the negated id
     */
    public static int inheritedId(int groupFieldId) {
        return -groupFieldId;
    }

    /**
     * The event group's field a field id names.
     *
     * @param fieldId the id of a field of the event group as shown on a part
     * @return the id of the event group's field
     * @throws IllegalStateException for the id of an appointment's own field
     */
    public static int groupFieldId(int fieldId) {
        if (!inherited(fieldId)) throw new IllegalStateException("Field " + fieldId + " is an appointment's own");
        return -fieldId;
    }

    /**
     * The same question, telling the reader asking whether they may put themselves into it.
     *
     * @param state what the reader may do, or null to say nothing
     * @return the question with that state
     */
    public AppointmentField withSelfRegistration(@Nullable SelfRegistration state) {
        return new AppointmentField(
                id,
                eventId,
                name,
                fieldType,
                config,
                value,
                position,
                overview,
                attendanceFieldId,
                isPublic,
                state,
                readerInWholeGroup);
    }

    /**
     * The same member field of an event group on one of its appointments, telling the reader asking
     * whether they stand in it for the whole group.
     *
     * @param inWholeGroup whether they do
     * @return the field with that said
     */
    public AppointmentField withReaderInWholeGroup(boolean inWholeGroup) {
        return new AppointmentField(
                id,
                eventId,
                name,
                fieldType,
                config,
                value,
                position,
                overview,
                attendanceFieldId,
                isPublic,
                selfRegistrationState,
                inWholeGroup);
    }

    /**
     * The same question with another answer, such as the one an appointment of an event group shows
     * once its own entries are taken into account.
     *
     * @param shownValue the answer
     * @return the question with that answer
     */
    public AppointmentField withValue(String shownValue) {
        return new AppointmentField(
                id,
                eventId,
                name,
                fieldType,
                config,
                shownValue,
                position,
                overview,
                attendanceFieldId,
                isPublic,
                selfRegistrationState,
                readerInWholeGroup);
    }

    /**
     * Where an appointment takes place: the first location question it answers.
     *
     * @param fields the questions of the appointment, in their order
     * @return the place, trimmed, or null where no location question is answered
     */
    public static @Nullable String firstLocation(List<AppointmentField> fields) {
        for (var field : fields) {
            if (field.fieldType() == FieldType.LOCATION
                    && field.value() != null
                    && !field.value().isBlank()) {
                return field.value().trim();
            }
        }
        return null;
    }

    /**
     * The same question tied to another field of the attendance sheet.
     *
     * @param sheetFieldId the sheet field it fills in
     * @return the question with that tie
     */
    public AppointmentField withAttendanceField(int sheetFieldId) {
        return new AppointmentField(
                id,
                eventId,
                name,
                fieldType,
                config,
                value,
                position,
                overview,
                sheetFieldId,
                isPublic,
                selfRegistrationState,
                readerInWholeGroup);
    }

    /**
     * The key two field names are matched by when an appointment field replaces a field of its event
     * group: the name without surrounding space, in lower case.
     *
     * @param name a field name
     * @return its key
     */
    public static String nameKey(String name) {
        return name.strip().toLowerCase(Locale.ROOT);
    }

    /**
     * What an appointment shows: the fields its event group hands down, then its own. A group field
     * whose name an own field carries is left out, because the own one replaces it.
     *
     * @param own       the appointment's own fields
     * @param inherited the fields its event group hands down
     * @param ownNames  the names of all of the appointment's own fields, which may be more than
     *                  {@code own} where only some are read
     * @return the fields the appointment shows
     */
    public static List<AppointmentField> shown(
            List<AppointmentField> own, List<AppointmentField> inherited, Collection<String> ownNames) {
        if (inherited.isEmpty()) return own;
        var replaced = ownNames.stream().map(AppointmentField::nameKey).collect(Collectors.toSet());
        var shown = new ArrayList<AppointmentField>();
        inherited.stream()
                .filter(field -> !replaced.contains(nameKey(field.name())))
                .forEach(shown::add);
        shown.addAll(own);
        return shown;
    }

    /**
     * The fields that fill in an appointment's attendance sheet: what it shows, with every own field
     * that replaces a tied field of its event group, is of the same kind and carries no tie of its own
     * taking over that tie. A field of another kind would write a value of the wrong shape.
     * An appointment that needs another value than its group puts an own field of the same name in its
     * place, and that value belongs on the sheet just as the group's would have been.
     *
     * @param own       the appointment's own fields
     * @param inherited the fields its event group hands down, as the appointment shows them
     * @return the fields the appointment shows, each with the sheet field it fills in, if any
     */
    public static List<AppointmentField> fillingTheSheet(List<AppointmentField> own, List<AppointmentField> inherited) {
        var tiedGroupFields = new HashMap<String, AppointmentField>();
        inherited.stream()
                .filter(field -> field.attendanceFieldId() != null)
                .forEach(field -> tiedGroupFields.putIfAbsent(nameKey(field.name()), field));
        var carried = own.stream()
                .map(field -> {
                    var replaced = tiedGroupFields.get(nameKey(field.name()));
                    return field.attendanceFieldId() == null
                                    && replaced != null
                                    && replaced.fieldType() == field.fieldType()
                            ? field.withAttendanceField(Objects.requireNonNull(replaced.attendanceFieldId()))
                            : field;
                })
                .toList();
        return shown(
                carried, inherited, own.stream().map(AppointmentField::name).toList());
    }

    /** The question this field asks, as the one check measures an answer against it. */
    public Question question() {
        return config.asQuestion(name, fieldType);
    }

    public static RowMapping<AppointmentField> map() {
        return row -> new AppointmentField(
                row.getInt("id"),
                row.getInt("event_id"),
                row.getString("name"),
                row.getEnum("field_type", FieldType.class),
                EventQuestionSettings.parse(row.getString("config")),
                row.getString("value"),
                row.getInt("position"),
                row.getBoolean("overview"),
                row.getObject("attendance_field_id", Integer.class),
                row.getBoolean("public"));
    }
}
