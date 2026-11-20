/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.maps.entity;

import org.jspecify.annotations.Nullable;

import java.util.Arrays;

/**
 * Who looks up addresses for this instance. {@link #NONE} keeps every address on the instance;
 * {@link #PHOTON} asks a Photon server, the public one or one the operator runs.
 */
public enum GeocodingProvider {
    NONE,
    PHOTON;

    /**
     * Reads a stored provider name. A provider this version no longer offers, or no name at all,
     * reads as {@link #NONE}, so a setting saved by an older version switches the lookup off
     * rather than failing to load.
     *
     * @param name the stored name, or null
     * @return the provider it names, or {@link #NONE}
     */
    public static GeocodingProvider fromStored(@Nullable String name) {
        return Arrays.stream(values())
                .filter(provider -> provider.name().equals(name))
                .findFirst()
                .orElse(NONE);
    }
}
