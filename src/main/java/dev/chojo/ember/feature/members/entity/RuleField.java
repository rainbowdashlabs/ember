/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.entity;

import dev.chojo.ember.feature.question.FieldType;

import java.util.List;
import java.util.Objects;

/**
 * A field a check of a rule can read: one of the station's own or one its association asks.
 *
 * @param origin    whether the station or its association asks it
 * @param id        the field
 * @param name      its name
 * @param fieldType its type, which decides the operators a check offers on it
 * @param options   the answers a choice offers, empty for every other type
 */
public record RuleField(FieldOrigin origin, int id, String name, FieldType fieldType, List<String> options) {

    /** A field with the answers its settings offer. */
    public static RuleField of(
            FieldOrigin origin, int id, String name, FieldType fieldType, ProfileFieldConfig config) {
        var options = config.options();
        return new RuleField(
                origin,
                id,
                name,
                fieldType,
                options == null
                        ? List.of()
                        : options.stream().filter(Objects::nonNull).toList());
    }
}
