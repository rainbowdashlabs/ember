/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.entity;

import com.fasterxml.jackson.annotation.JsonInclude;
import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.feature.question.QuestionConfigs;
import dev.chojo.ember.feature.question.QuestionSettings;

import java.util.List;

/**
 * Configuration for an event custom field, stored as JSONB.
 *
 * @param options          selectable values for {@code ENUM}-type fields
 * @param groupId          referenced member group for {@code *_OF_GROUP} fields
 * @param userType         referenced user type for {@code *_OF_TYPE} fields
 * @param tagId            referenced user tag for {@code *_OF_TAG} fields
 * @param selfRegistration when {@code true}, station members can add or remove themselves
 *                         on a {@code MEMBER_*} field without the edit-event permission
 * @param width            how much of a row the field takes when the form is drawn, which is the
 *                         station's own layout choice and means nothing to the server
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record EventFieldConfig(
        List<String> options,
        Integer groupId,
        StationUserType userType,
        Integer tagId,
        boolean selfRegistration,
        String width) {
    private static final EventFieldConfig EMPTY = new EventFieldConfig(null, null, null, null, false, null);

    public static EventFieldConfig empty() {
        return EMPTY;
    }

    public static EventFieldConfig parse(String json) {
        return QuestionConfigs.parse(json, EventFieldConfig.class, EMPTY);
    }

    public String toJson() {
        return QuestionConfigs.toJson(this);
    }

    /** What this field says about the question it asks, as everything that measures one reads it. */
    public QuestionSettings settings() {
        return QuestionSettings.none().withOptions(options);
    }
}
