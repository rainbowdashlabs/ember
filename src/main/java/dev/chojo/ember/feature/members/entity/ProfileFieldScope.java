/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.entity;

/** Defines which roles can see and interact with a profile field. */
public enum ProfileFieldScope {
    MEMBER,
    GUARDIAN,
    TEAM,
    GROUP
}
