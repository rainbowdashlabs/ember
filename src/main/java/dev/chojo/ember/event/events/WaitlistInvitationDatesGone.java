/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.event.events;

import dev.chojo.ember.event.DomainEvent;

/**
 * Every date an open invitation offered has begun without an answer. The family is no longer reminded
 * of it, so the station is told, to send other dates or take the person back to waiting.
 */
public record WaitlistInvitationDatesGone(int stationId, String applicantName, String listName)
        implements DomainEvent {}
