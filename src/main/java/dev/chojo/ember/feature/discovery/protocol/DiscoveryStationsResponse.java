/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.discovery.protocol;

import dev.chojo.ember.feature.discovery.entity.DiscoveryStationCard;

import java.util.List;

/**
 * Body of {@code GET /api/v1/public/discovery/stations}. Wraps the originating instance's
 * identity together with the list of {@code PUBLIC}-scoped station cards.
 */
public record DiscoveryStationsResponse(DiscoveryIdentity instance, List<DiscoveryStationCard> stations) {}
