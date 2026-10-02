/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.entity;

import com.fasterxml.jackson.annotation.JsonInclude;
import dev.chojo.ember.api.auth.StationUserType;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * The settings of an appointment's question as screens and partner stations read them.
 *
 * <p>Only what crosses the wire speaks this shape; the server keeps every question's settings as
 * {@link EventQuestionSettings}. The components stay because a partner station compares them before it
 * shares an appointment.
 *
 * @param options          selectable values for a choice
 * @param groupId          referenced member group for {@code *_OF_GROUP} fields
 * @param userType         referenced user type for {@code *_OF_TYPE} fields
 * @param tagId            referenced user tag for {@code *_OF_TAG} fields
 * @param selfRegistration when {@code true}, station members can add or remove themselves on a member
 *                         field without the edit-event permission
 * @param width            how much of a row the field takes when the form is drawn
 * @param perDate          when {@code true}, the field carries one answer per date of the appointment
 *                         rather than one for the whole series
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record EventFieldConfig(
        @Nullable List<String> options,
        @Nullable Integer groupId,
        @Nullable StationUserType userType,
        @Nullable Integer tagId,
        boolean selfRegistration,
        @Nullable String width,
        boolean perDate) {
    private static final EventFieldConfig EMPTY = new EventFieldConfig(null, null, null, null, false, null, false);

    public static EventFieldConfig empty() {
        return EMPTY;
    }

    /** The wire shape of a question's settings. */
    public static EventFieldConfig of(EventQuestionSettings settings) {
        return new EventFieldConfig(
                settings.options(),
                settings.groupId(),
                settings.userType(),
                settings.tagId(),
                settings.selfRegistration(),
                settings.width(),
                settings.perDate());
    }

    /** These settings as the server keeps them. */
    public EventQuestionSettings settings() {
        return new EventQuestionSettings(
                options, groupId, userType, tagId, width, selfRegistration, perDate, false, null, null, null, false);
    }
}
