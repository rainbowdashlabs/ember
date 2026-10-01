/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.entity;

import org.jspecify.annotations.Nullable;

/**
 * A question of an appointment as it arrives to be written, naming the row it overwrites or none.
 *
 * @param id the row this overwrites, or null for a question asked for the first time
 */
public record EventFieldDraft(
        @Nullable Integer id,
        String name,
        EventFieldType fieldType,
        EventFieldConfig config,
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
            EventFieldType fieldType,
            EventFieldConfig config,
            String value,
            boolean overview,
            @Nullable Integer attendanceFieldId,
            boolean isPublic) {
        this(null, name, fieldType, config, value, overview, attendanceFieldId, isPublic);
    }
}
