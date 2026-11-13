/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.entity;

/**
 * What a listed appointment says about the event group it is a part of: "Jugendflamme 1, part 2 of 4".
 *
 * @param eventGroupId   the event group
 * @param eventGroupName its name
 * @param setId      the set the part belongs to
 * @param setName    the set's name
 * @param number     the part's place among the event group's parts in date order, from one
 * @param count      how many parts the event group has
 */
public record EventGroupPartSummary(
        int eventGroupId, String eventGroupName, int setId, String setName, int number, int count) {}
