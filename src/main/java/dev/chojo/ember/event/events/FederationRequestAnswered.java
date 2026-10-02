/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.event.events;

import dev.chojo.ember.event.DomainEvent;

/**
 * A station a station here asked to federate gave its answer.
 *
 * @param stationId            the asking station
 * @param answeringStationName the asked station's name
 * @param accepted             whether it agreed
 */
public record FederationRequestAnswered(int stationId, String answeringStationName, boolean accepted)
        implements DomainEvent {}
