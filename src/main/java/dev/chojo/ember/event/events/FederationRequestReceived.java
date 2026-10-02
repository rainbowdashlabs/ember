/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.event.events;

import dev.chojo.ember.event.DomainEvent;

/**
 * A station asked a station here to federate, from this instance or from another one.
 *
 * @param stationId             the asked station
 * @param requestingStationName the asking station's name
 */
public record FederationRequestReceived(int stationId, String requestingStationName) implements DomainEvent {}
