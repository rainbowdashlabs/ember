/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.maps.repository;

import de.chojo.sadu.postgresql.types.PostgreSqlTypes;
import dev.chojo.ember.feature.maps.entity.GeocodePrecision;
import dev.chojo.ember.feature.maps.entity.StationPlace;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;

/**
 * The map pins of the place texts a station's appointments name, one per station and place text in
 * its normal form. The caller normalises; this class stores and finds by the key it is given.
 */
@Singleton
public class StationPlaceRepository {

    /**
     * Stores the pin of a place, replacing the one the same key had.
     *
     * @param stationId the station
     * @param key       the place text in its normal form
     * @param text      the place text as typed
     * @param latitude  degrees north
     * @param longitude degrees east
     * @param precision how closely the address search matched, or null when placed by hand
     * @return the stored pin
     */
    public StationPlace save(
            int stationId,
            String key,
            String text,
            BigDecimal latitude,
            BigDecimal longitude,
            @Nullable GeocodePrecision precision) {
        return query("""
                INSERT INTO station_place (station_id, place_key, place_text, latitude, longitude, precision, updated_at)
                VALUES (:station_id, :place_key, :place_text, :latitude, :longitude, :precision, now())
                ON CONFLICT (station_id, place_key)
                    DO UPDATE SET
                        place_text = excluded.place_text,
                        latitude   = excluded.latitude,
                        longitude  = excluded.longitude,
                        precision  = excluded.precision,
                        updated_at = excluded.updated_at
                RETURNING %s;""", StationPlace.COLUMNS)
                .single(call().bind("station_id", stationId)
                        .bind("place_key", key)
                        .bind("place_text", text)
                        .bind("latitude", latitude)
                        .bind("longitude", longitude)
                        .bind("precision", precision))
                .map(StationPlace.map())
                .first()
                .orElseThrow();
    }

    /**
     * The pin of one place.
     *
     * @param stationId the station
     * @param key       the place text in its normal form
     * @return the pin, or empty when the place has none
     */
    public Optional<StationPlace> find(int stationId, String key) {
        return query("""
                SELECT %s
                FROM station_place
                WHERE station_id = :station_id
                  AND place_key = :place_key;""", StationPlace.COLUMNS)
                .single(call().bind("station_id", stationId).bind("place_key", key))
                .map(StationPlace.map())
                .first();
    }

    /**
     * Takes the pin off one place, which every appointment naming it then shows without a map.
     *
     * @param stationId the station
     * @param key       the place text in its normal form
     * @return whether the place had a pin
     */
    public boolean delete(int stationId, String key) {
        return query("DELETE FROM station_place WHERE station_id = :station_id AND place_key = :place_key;")
                .single(call().bind("station_id", stationId).bind("place_key", key))
                .delete()
                .changed();
    }

    /**
     * The pins of several places at once, for every key that has one.
     *
     * @param stationId the station
     * @param keys      the place texts in their normal form
     * @return the pins found, in no particular order
     */
    public List<StationPlace> findAll(int stationId, Collection<String> keys) {
        if (keys.isEmpty()) return List.of();
        return query("""
                SELECT %s
                FROM station_place
                WHERE station_id = :station_id
                  AND place_key = ANY(:place_keys);""", StationPlace.COLUMNS)
                .single(call().bind("station_id", stationId)
                        .bind("place_keys", List.copyOf(keys), PostgreSqlTypes.TEXT))
                .map(StationPlace.map())
                .all();
    }
}
