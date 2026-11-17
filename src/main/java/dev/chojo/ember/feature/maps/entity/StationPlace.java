/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.maps.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;
import org.jspecify.annotations.Nullable;

import java.time.Instant;

import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

/**
 * The map pin of one place text a station's appointments name.
 *
 * @param text      the place text as it was last saved
 * @param latitude  degrees north
 * @param longitude degrees east
 * @param precision how closely the address search matched when it placed the pin, or null when
 *                  the pin was placed or moved by hand
 * @param updatedAt when the pin was last saved
 */
public record StationPlace(
        String text,
        double latitude,
        double longitude,
        @Nullable GeocodePrecision precision,
        Instant updatedAt) {

    /** The columns {@link #map()} reads. */
    public static final String COLUMNS = "place_text, latitude, longitude, precision, updated_at";

    /**
     * Creates a row mapping for database result set conversion.
     *
     * @return the mapping
     */
    public static RowMapping<StationPlace> map() {
        return row -> new StationPlace(
                row.getString("place_text"),
                row.getDouble("latitude"),
                row.getDouble("longitude"),
                row.getString("precision") == null ? null : row.getEnum("precision", GeocodePrecision.class),
                row.get("updated_at", INSTANT_TIMESTAMP));
    }
}
