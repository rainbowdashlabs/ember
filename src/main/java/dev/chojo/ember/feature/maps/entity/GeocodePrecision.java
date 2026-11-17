/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.maps.entity;

import org.jspecify.annotations.Nullable;

/** How closely a found pin matches what was searched for. */
public enum GeocodePrecision {
    /** The building itself. */
    EXACT,
    /** The street, not the building. */
    STREET,
    /** Only the town, district or a wider area. */
    AREA;

    /**
     * Reads the precision from the {@code type} Photon gives a match.
     *
     * @param photonType the type, such as {@code house}, {@code street} or {@code city}
     * @return the precision it stands for, {@link #AREA} for every type that is neither a house nor a street
     */
    public static GeocodePrecision ofPhotonType(@Nullable String photonType) {
        if ("house".equals(photonType)) return EXACT;
        if ("street".equals(photonType)) return STREET;
        return AREA;
    }
}
