/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.station.service;

import dev.chojo.ember.api.refusal.Refusal;
import dev.chojo.ember.api.refusal.StationRefusal;
import dev.chojo.ember.feature.maps.entity.GeocodeQuery;
import dev.chojo.ember.feature.maps.entity.GeocodeResult;
import dev.chojo.ember.feature.maps.service.GeocodingService;
import dev.chojo.ember.feature.station.repository.StationRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Geolocation read/write for stations. Validation rules live here; the route layer is a
 * thin delegate.
 */
@Singleton
public class StationLocationService {
    private static final Logger log = LoggerFactory.getLogger(StationLocationService.class);
    private static final int MAX_TEXT_LEN = 200;
    private static final Pattern COUNTRY_RX = Pattern.compile("^[A-Z]{2}$");
    private static final BigDecimal LAT_MIN = BigDecimal.valueOf(-90);
    private static final BigDecimal LAT_MAX = BigDecimal.valueOf(90);
    private static final BigDecimal LON_MIN = BigDecimal.valueOf(-180);
    private static final BigDecimal LON_MAX = BigDecimal.valueOf(180);
    private static final double EARTH_KM = 6371.0;

    private final StationRepository repository;
    private final GeocodingService geocoding;

    @Inject
    public StationLocationService(StationRepository repository, GeocodingService geocoding) {
        this.repository = repository;
        this.geocoding = geocoding;
    }

    /**
     * Java-side haversine that mirrors the SQL function for use against in-memory station
     * coordinates (e.g. when ranking federated lending results by distance from the local
     * station against a remote partner whose coordinates we already cached).
     */
    public static double distanceKm(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.pow(Math.sin(dLat / 2), 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.pow(Math.sin(dLon / 2), 2);
        return 2 * EARTH_KM * Math.asin(Math.sqrt(a));
    }

    private static void validate(LocationUpdate update) {
        if (update == null) throw StationRefusal.STATION_LOCATION_MISSING.raise();
        BigDecimal latitude = update.latitude();
        BigDecimal longitude = update.longitude();
        String country = update.country();
        if ((latitude == null) != (longitude == null)) {
            throw StationRefusal.STATION_LOCATION_HALF_PINNED.raise();
        }
        if (latitude != null && (latitude.compareTo(LAT_MIN) < 0 || latitude.compareTo(LAT_MAX) > 0)) {
            throw StationRefusal.STATION_LATITUDE_OUT_OF_RANGE.raise();
        }
        if (longitude != null && (longitude.compareTo(LON_MIN) < 0 || longitude.compareTo(LON_MAX) > 0)) {
            throw StationRefusal.STATION_LONGITUDE_OUT_OF_RANGE.raise();
        }
        validateAddress(update.addressLine(), update.postalCode(), update.city(), country);
    }

    private static void validateAddress(
            @Nullable String addressLine,
            @Nullable String postalCode,
            @Nullable String city,
            @Nullable String country) {
        if (country != null
                && !country.isBlank()
                && !COUNTRY_RX.matcher(country.trim()).matches()) {
            throw StationRefusal.STATION_COUNTRY_NOT_A_CODE.raise();
        }
        rejectIfTooLong(StationRefusal.STATION_ADDRESS_LINE_TOO_LONG, addressLine);
        rejectIfTooLong(StationRefusal.STATION_POSTAL_CODE_TOO_LONG, postalCode);
        rejectIfTooLong(StationRefusal.STATION_CITY_TOO_LONG, city);
    }

    private static void rejectIfTooLong(Refusal refusal, @Nullable String value) {
        if (value != null && value.length() > MAX_TEXT_LEN) {
            throw refusal.raise();
        }
    }

    private static @Nullable String trimToNull(@Nullable String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * Reads the location for a station. Fields are independently nullable.
     */
    public LocationView find(int stationId) {
        return repository
                .findById(stationId)
                .map(s -> new LocationView(
                        s.addressLine(), s.postalCode(), s.city(), s.country(), s.latitude(), s.longitude()))
                .orElseThrow(StationRefusal.STATION_NOT_HERE_FOR_LOCATION::raise);
    }

    /**
     * Updates the location block. Each field is independently nullable but if either
     * coordinate is supplied the other must be too (a half-pinned coordinate is rejected).
     */
    public void update(int stationId, LocationUpdate update) {
        validate(update);
        repository.updateLocation(
                stationId,
                trimToNull(update.addressLine()),
                trimToNull(update.postalCode()),
                trimToNull(update.city()),
                trimToNull(update.country()),
                update.latitude(),
                update.longitude());
        log.info(
                "Station location updated: station={}, city={}, country={}, hasCoords={}",
                stationId,
                update.city(),
                update.country(),
                update.latitude() != null && update.longitude() != null);
    }

    /**
     * Looks up where an address lies, as typed and not yet saved. The address is held to the same
     * rules as a saved one, so a country that is not a two-letter code is refused here rather than
     * sent to the address search. Nothing is stored.
     *
     * @param search the address fields as the form holds them
     * @return where the address search put the pin, or empty when it found nothing
     */
    public Optional<GeocodeResult> locate(AddressSearch search) {
        validateAddress(search.addressLine(), search.postalCode(), search.city(), search.country());
        return geocoding.locate(GeocodeQuery.address(
                search.addressLine(), search.postalCode(), search.city(), trimToNull(search.country())));
    }

    /**
     * Convenience: clear every column in one call. Used by the "Standort entfernen" button.
     */
    public void clear(int stationId) {
        repository.updateLocation(stationId, null, null, null, null, null, null);
        log.info("Station location cleared: station={}", stationId);
    }

    /**
     * Returns local station ids within the given radius of the origin, sorted ascending by
     * distance. Empty when no station has coordinates.
     */
    public List<StationRepository.StationDistance> findStationsWithinRadius(
            BigDecimal originLat, BigDecimal originLon, double radiusKm) {
        if (originLat == null || originLon == null) return List.of();
        if (radiusKm <= 0) return List.of();
        return repository.findStationsWithinRadius(originLat, originLon, radiusKm);
    }

    /**
     * Response payload for {@code GET /station/location}.
     */
    public record LocationView(
            @Nullable String addressLine,
            @Nullable String postalCode,
            @Nullable String city,
            @Nullable String country,
            @Nullable BigDecimal latitude,
            @Nullable BigDecimal longitude) {}

    /**
     * Request payload for {@code POST /station/location/geocode}: the address fields as typed.
     */
    public record AddressSearch(
            @Nullable String addressLine,
            @Nullable String postalCode,
            @Nullable String city,
            @Nullable String country) {}

    /**
     * Request payload for {@code PUT /station/location}.
     */
    public record LocationUpdate(
            @Nullable String addressLine,
            @Nullable String postalCode,
            @Nullable String city,
            @Nullable String country,
            @Nullable BigDecimal latitude,
            @Nullable BigDecimal longitude) {}
}
