/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.entity;

public record LayoutFieldEntry(
        String name, String fieldType, String config, boolean overview, Integer attendanceFieldId) {}
