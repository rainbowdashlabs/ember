/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.entity;

import dev.chojo.ember.feature.question.FieldType;
import dev.chojo.ember.feature.question.Question;
import org.jspecify.annotations.Nullable;

/**
 * A question of an appointment as it arrives to be written, naming the row it overwrites or none.
 *
 * @param id the row this overwrites, or null for a question asked for the first time
 */
public record EventFieldDraft(
        @Nullable Integer id,
        String name,
        FieldType fieldType,
        EventQuestionSettings config,
        String value,
        boolean overview,
        @Nullable Integer attendanceFieldId,
        boolean isPublic) {

    /**
     * A question the caller does not name a row for, which is one being asked for the first
     * time. Everything that builds questions from scratch, a template or an import says it this
     * way; only the editor, which is writing over questions that already exist, names ids.
     */
    public EventFieldDraft(
            String name,
            FieldType fieldType,
            EventQuestionSettings config,
            String value,
            boolean overview,
            @Nullable Integer attendanceFieldId,
            boolean isPublic) {
        this(null, name, fieldType, config, value, overview, attendanceFieldId, isPublic);
    }

    /** The question this draft asks, as the one check measures its answer against it. */
    public Question question() {
        return config.asQuestion(name, fieldType);
    }

    /** The same draft with the answer in the one shape its type is stored in. */
    public EventFieldDraft withValue(String stored) {
        return new EventFieldDraft(id, name, fieldType, config, stored, overview, attendanceFieldId, isPublic);
    }

    /** The same draft without its tie to a field of an attendance sheet. */
    public EventFieldDraft untied() {
        return new EventFieldDraft(id, name, fieldType, config, value, overview, null, isPublic);
    }
}
