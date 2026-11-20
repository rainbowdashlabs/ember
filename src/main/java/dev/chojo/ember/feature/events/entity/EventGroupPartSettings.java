/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.entity;

import org.jspecify.annotations.Nullable;

/**
 * What a part of an event group sets itself and what it takes over, for the part's editor. The
 * appointment itself is read with the settings as they take effect, so its own ones come from here.
 *
 * @param eventGroupId   the event group
 * @param ownTemplateId  the part's own attendance template, or null where it follows the event group
 * @param ownCategoryId  the part's own category, or null where it follows the event group
 * @param eventGroup     what the event group hands to its parts
 * @param ownRegistrationLimit  the part's own places, or null where it takes the event group's
 * @param partRegistrationLimit the places per part the event group gives, or null for no limit
 */
public record EventGroupPartSettings(
        int eventGroupId,
        @Nullable Integer ownTemplateId,
        @Nullable Integer ownCategoryId,
        EventGroupSettings eventGroup,
        @Nullable Integer ownRegistrationLimit,
        @Nullable Integer partRegistrationLimit) {}
