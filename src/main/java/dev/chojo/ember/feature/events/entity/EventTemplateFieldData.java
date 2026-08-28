/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.entity;

/**
 * One question of an event template, as it arrives to be stored.
 *
 * @param defaultValue what an appointment made from this template starts the question off with, or
 *                     null where it starts empty
 */
public record EventTemplateFieldData(
        String name,
        EventFieldType fieldType,
        EventFieldConfig config,
        int position,
        boolean overview,
        boolean isPublic,
        Integer attendanceFieldId,
        String defaultValue) {}
