/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.maps.entity;

import org.jspecify.annotations.Nullable;

/**
 * Response payload of an address lookup.
 *
 * @param match where the pin goes, or null when the address search found nothing
 */
public record GeocodeAnswer(@Nullable GeocodeResult match) {}
