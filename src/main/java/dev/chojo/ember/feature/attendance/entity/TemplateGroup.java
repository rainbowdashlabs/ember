/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.attendance.entity;

/**
 * Associates a member group with a template at a given position.
 *
 * @param groupId  the group ID
 * @param position ordering position
 */
public record TemplateGroup(int groupId, int position) {}
