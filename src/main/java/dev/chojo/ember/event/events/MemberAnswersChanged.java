/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.event.events;

import dev.chojo.ember.event.DomainEvent;

/**
 * A member's profile answers changed, to the station's own questions or to its association's.
 *
 * @param stationId the station the member belongs to
 * @param memberId  the member whose answers changed
 */
public record MemberAnswersChanged(int stationId, int memberId) implements DomainEvent {}
