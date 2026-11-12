/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.station.entity;

/**
 * A reminder sent by hand: the station as it stands afterwards, and what the reminder came to.
 *
 * @param station the station after the reminder
 * @param result  whether a mail went out
 */
public record ReminderAnswer(StationOverview station, ReminderResult result) {}
