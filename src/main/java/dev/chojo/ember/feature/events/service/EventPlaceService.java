/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.service;

import dev.chojo.ember.api.refusal.EventRefusal;
import dev.chojo.ember.feature.events.entity.AppointmentField;
import dev.chojo.ember.feature.maps.entity.GeocodePrecision;
import dev.chojo.ember.feature.maps.entity.GeocodeQuery;
import dev.chojo.ember.feature.maps.entity.GeocodeResult;
import dev.chojo.ember.feature.maps.entity.StationPlace;
import dev.chojo.ember.feature.maps.repository.StationPlaceRepository;
import dev.chojo.ember.feature.maps.service.GeocodingService;
import dev.chojo.ember.feature.question.FieldType;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.repository.StationRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;

/**
 * The map pins of the places a station's appointments name.
 *
 * <p>A place field holds plain text, and calendars, announcements and feeds read it as text. The pin
 * lives beside it, one per station and place text in its normal form (trimmed, lower case, runs of
 * whitespace made one), so "Gerätehaus" named by fifty appointments is found and corrected once,
 * and a pin dragged on one of them moves for all of them. Saving a pin never touches a field value.
 *
 * <p>Finding a place asks the address search with the text alone, limited to the station's country
 * and preferring matches near the station where the station has a pin of its own.
 */
@Singleton
public class EventPlaceService {
    private static final int MAX_TEXT_LENGTH = 200;
    private static final int COORDINATE_SCALE = 6;
    private static final double LATITUDE_LIMIT = 90;
    private static final double LONGITUDE_LIMIT = 180;

    private final StationPlaceRepository places;
    private final StationRepository stations;
    private final GeocodingService geocoding;

    @Inject
    public EventPlaceService(StationPlaceRepository places, StationRepository stations, GeocodingService geocoding) {
        this.places = places;
        this.stations = stations;
        this.geocoding = geocoding;
    }

    private static String requirePlaceText(@Nullable String text) {
        String trimmed = text == null ? "" : text.trim();
        if (trimmed.isEmpty()) throw EventRefusal.PLACE_TEXT_MISSING.raise();
        if (trimmed.length() > MAX_TEXT_LENGTH) throw EventRefusal.PLACE_TEXT_TOO_LONG.raise();
        return trimmed;
    }

    private static BigDecimal coordinate(@Nullable Double value, double limit) {
        if (value == null || !Double.isFinite(value) || Math.abs(value) > limit) {
            throw EventRefusal.PLACE_OFF_THE_MAP.raise();
        }
        return BigDecimal.valueOf(value).setScale(COORDINATE_SCALE, RoundingMode.HALF_UP);
    }

    /**
     * The stored pin of a place text.
     *
     * @param stationId the station
     * @param text      the place text as typed
     * @return the pin, or empty when the text has none or is blank
     */
    public Optional<StationPlace> find(int stationId, @Nullable String text) {
        if (text == null || text.isBlank()) return Optional.empty();
        return places.find(stationId, GeocodeQuery.normalise(text));
    }

    /**
     * Stores the pin of a place text, replacing the one it had.
     *
     * @param stationId the station
     * @param draft     the place text and where its pin goes
     * @return the stored pin
     */
    public StationPlace save(int stationId, PlaceDraft draft) {
        String text = requirePlaceText(draft.text());
        return places.save(
                stationId,
                GeocodeQuery.normalise(text),
                text,
                coordinate(draft.latitude(), LATITUDE_LIMIT),
                coordinate(draft.longitude(), LONGITUDE_LIMIT),
                draft.precision());
    }

    /**
     * Looks a place text up on the map, limited to the station's country and preferring matches
     * near the station. Nothing is stored.
     *
     * @param stationId the station
     * @param text      the place text as typed
     * @return where the address search put the pin, or empty when it found nothing
     */
    public Optional<GeocodeResult> locate(int stationId, @Nullable String text) {
        String place = requirePlaceText(text);
        Optional<Station> station = stations.findById(stationId);
        String country = station.map(Station::country).orElse(null);
        BigDecimal latitude = station.map(Station::latitude).orElse(null);
        BigDecimal longitude = station.map(Station::longitude).orElse(null);
        boolean biased = latitude != null && longitude != null;
        return geocoding.locate(new GeocodeQuery(
                place, country, biased ? latitude.doubleValue() : null, biased ? longitude.doubleValue() : null));
    }

    /**
     * The pins of the place fields among the given fields, for every place text that has one.
     *
     * @param stationId the station the fields belong to
     * @param fields    the fields a reader sees
     * @return the pins, each once, in no particular order
     */
    public List<StationPlace> pinsOf(int stationId, Collection<AppointmentField> fields) {
        var keys = new LinkedHashSet<String>();
        for (var field : fields) {
            if (field.fieldType() != FieldType.LOCATION || field.value().isBlank()) continue;
            keys.add(GeocodeQuery.normalise(field.value()));
        }
        return places.findAll(stationId, keys);
    }

    /**
     * Request payload for saving the pin of a place.
     *
     * @param text      the place text, as the field holds it
     * @param latitude  degrees north; a draft without one is refused rather than read as zero
     * @param longitude degrees east; likewise
     * @param precision how closely the address search matched, or null when the pin was placed or
     *                  moved by hand
     */
    public record PlaceDraft(
            @Nullable String text,
            @Nullable Double latitude,
            @Nullable Double longitude,
            @Nullable GeocodePrecision precision) {}
}
