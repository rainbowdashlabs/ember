/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.event.events;

import dev.chojo.ember.event.DomainEvent;
import dev.chojo.ember.feature.waitinglist.entity.WaitingListAnswer;

/**
 * Somebody on a waiting list has answered the invitation they were sent.
 *
 * <p>An answer that only appears on a screen somebody has to visit is not an answer the station
 * receives, which is why it travels rather than sitting on the entry alone.
 */
public record WaitlistInvitationAnswered(int stationId, String applicantName, String listName, WaitingListAnswer answer)
        implements DomainEvent {}
