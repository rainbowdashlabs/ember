/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;
import dev.chojo.ember.feature.question.Question;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

/**
 * A question a station asks about a member, defined once.
 *
 * <p>Who is asked it is not here. A field used to carry its audience, so the same question put to two
 * kinds of member was two rows with two answers, and a manager, who is asked the team's questions as
 * well as their own, saw it twice. The audiences live in {@link ProfileFieldAssignment} now, and this
 * says only what the question is.
 *
 * @param id            the field identifier
 * @param stationId     the station this field belongs to
 * @param name          the display name of the field, unique within its station
 * @param fieldType     the type of the field (e.g. TEXT, NUMBER, DATE, BOOLEAN, ENUM, AGE)
 * @param config        the field configuration stored as JSON (options, default, age mode)
 * @param required      whether an answer is expected, which an assignment may override for its own
 *                      audience
 * @param readonly      whether only the member management may write the answer. The question's own,
 *                      for everybody asked it: who may write an answer is a property of the question
 *                      rather than something one audience is told and another is not.
 * @param width         how much of a row the question takes: {@code full}, {@code half} or
 *                      {@code third}, null meaning the whole row. An assignment may override it for
 *                      its own audience, because the same question sits on forms of different shapes.
 * @param keepOnArchive whether to retain this field's values when a member is archived
 */
public record ProfileField(
        int id,
        int stationId,
        String name,
        ProfileFieldType fieldType,
        ProfileFieldConfig config,
        boolean required,
        boolean readonly,
        @Nullable String width,
        boolean keepOnArchive) {
    /**
     * This field as everything that checks a question reads it, or nothing where it is a heading and
     * asks nobody anything.
     */
    public Optional<Question> question() {
        return fieldType.kind().map(kind -> config.settings(required).asQuestion(name, kind));
    }

    /**
     * Creates a row mapping for database result set conversion.
     */
    public static RowMapping<ProfileField> map() {
        return row -> new ProfileField(
                row.getInt("id"),
                row.getInt("station_id"),
                row.getString("name"),
                row.getEnum("field_type", ProfileFieldType.class),
                ProfileFieldConfig.parse(row.getString("config")),
                row.getBoolean("required"),
                row.getBoolean("readonly"),
                row.getString("width"),
                row.getBoolean("keep_on_archive"));
    }
}
