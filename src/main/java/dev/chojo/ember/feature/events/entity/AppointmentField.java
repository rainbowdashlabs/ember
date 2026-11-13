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
import java.util.List;
import java.util.Locale;
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
        @Nullable SelfRegistration selfRegistrationState) {

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
        this(id, eventId, name, fieldType, config, value, position, overview, attendanceFieldId, isPublic, null);
    }

    /**
     * The same question, telling the reader asking whether they may put themselves into it.
     *
     * @param state what the reader may do, or null to say nothing
     * @return the question with that state
     */
    public AppointmentField withSelfRegistration(@Nullable SelfRegistration state) {
        return new AppointmentField(
                id, eventId, name, fieldType, config, value, position, overview, attendanceFieldId, isPublic, state);
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
