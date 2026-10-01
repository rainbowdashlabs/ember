/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.route;

import dev.chojo.ember.feature.events.entity.EventFieldType;
import dev.chojo.ember.feature.events.entity.EventRegistrationFieldConfig;
import dev.chojo.ember.feature.events.entity.RegistrationFieldDraft;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

/**
 * One registration question as a request names it, for an event and for an event template alike.
 *
 * @param name      the question
 * @param fieldType the kind of answer it takes
 * @param config    its settings; none reads as the empty ones
 * @param overview  whether the answer shows in the registration overview
 */
public record RegistrationFieldDefinition(
        String name, EventFieldType fieldType, @Nullable EventRegistrationFieldConfig config, boolean overview) {

    RegistrationFieldDraft toDraft() {
        return new RegistrationFieldDraft(
                name,
                Objects.requireNonNullElse(fieldType, EventFieldType.STRING).fieldType(),
                Objects.requireNonNullElse(config, EventRegistrationFieldConfig.empty())
                        .settings(),
                overview);
    }
}
