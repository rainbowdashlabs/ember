/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.entity;

/**
 * One date of one appointment that is off, as a calendar of many appointments reads it.
 *
 * @param eventId      the appointment
 * @param cancellation which date is off and why
 */
public record CancelledEventDate(int eventId, CancellationNotice cancellation) {}
