/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.entity;

/**
 * A registration question carried by an event template, as screens read it.
 */
public record EventTemplateRegistrationField(
        int id,
        int templateId,
        String name,
        EventFieldType fieldType,
        EventRegistrationFieldConfig config,
        int position,
        boolean overview) {

    /** The shape screens read a template's registration question in. */
    public static EventTemplateRegistrationField of(RegistrationTemplateField field) {
        return new EventTemplateRegistrationField(
                field.id(),
                field.templateId(),
                field.name(),
                EventFieldType.of(field.fieldType()),
                EventRegistrationFieldConfig.of(field.config()),
                field.position(),
                field.overview());
    }
}
