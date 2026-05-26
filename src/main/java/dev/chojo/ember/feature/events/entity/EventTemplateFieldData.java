/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.entity;

public record EventTemplateFieldData(
        String name,
        String fieldType,
        String config,
        int position,
        boolean overview,
        boolean isPublic,
        Integer attendanceFieldId) {}
