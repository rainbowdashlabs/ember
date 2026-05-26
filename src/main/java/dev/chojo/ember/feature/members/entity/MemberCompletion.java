/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.entity;

/**
 * Lightweight member representation for autocomplete.
 *
 * @param id   the member ID
 * @param name the display name
 */
public record MemberCompletion(int id, String name) {}
