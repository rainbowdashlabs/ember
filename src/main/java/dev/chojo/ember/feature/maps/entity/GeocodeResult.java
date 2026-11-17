/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.maps.entity;

/**
 * Where the address search put a pin.
 *
 * @param latitude  degrees north
 * @param longitude degrees east
 * @param precision how closely the pin matches what was searched for
 */
public record GeocodeResult(double latitude, double longitude, GeocodePrecision precision) {}
