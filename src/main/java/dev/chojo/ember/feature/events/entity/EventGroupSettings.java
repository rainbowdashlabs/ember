/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.entity;

import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * The date settings an event group hands to every part without a setting of its own: a part's own
 * attendance template, category or reminders replace the event group's, and removing them brings the
 * event group's back.
 *
 * @param templateId    the attendance template the parts' sheets are taken from, or null for none
 * @param categoryId    the category the parts are filed under, or null for none
 * @param reminderDays  how many days before a part the people taking part are reminded, ascending
 * @param fieldDefaults the values filled into the sheet of a part that takes the event group's template,
 *                      each carrying the event group's id
 */
public record EventGroupSettings(
        @Nullable Integer templateId,
        @Nullable Integer categoryId,
        List<Integer> reminderDays,
        List<EventFieldDefault> fieldDefaults) {}
