/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.entity;

/**
 * A member a rule takes in or lets go, by name.
 *
 * @param id   the member
 * @param name how the station names the member
 */
public record RuleMember(int id, String name) {}
