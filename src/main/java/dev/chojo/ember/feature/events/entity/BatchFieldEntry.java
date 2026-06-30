/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.entity;

/**
 * Field definition copied onto every event created in a batch. Either supplied
 * inline on the batch request, or derived from the picked event template.
 */
public record BatchFieldEntry(
        String name, EventFieldType fieldType, EventFieldConfig config, boolean overview, Integer attendanceFieldId) {}
