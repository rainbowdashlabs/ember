/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.station.service;

import dev.chojo.ember.feature.station.repository.StationRepository;
import io.javalin.http.BadRequestResponse;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.math.BigDecimal;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Geolocation read/write for stations. Validation rules live here; the route layer is a
 * thin delegate. See {@code .concept/geolocation.md} for the design.
 */
@Singleton
public class StationLocationService {
    private static final int MAX_TEXT_LEN = 200;
    private static final Pattern COUNTRY_RX = Pattern.compile("^[A-Z]{2}$");
    private static final BigDecimal LAT_MIN = BigDecimal.valueOf(-90);
    private static final BigDecimal LAT_MAX = BigDecimal.valueOf(90);
    private static final BigDecimal LON_MIN = BigDecimal.valueOf(-180);
    private static final BigDecimal LON_MAX = BigDecimal.valueOf(180);
    private static final double EARTH_KM = 6371.0;

    private final StationRepository repository;

    @Inject
    public StationLocationService(StationRepository repository) {
        this.repository = repository;
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
        if (update == null) throw new BadRequestResponse("body required");
        if ((update.latitude() == null) != (update.longitude() == null)) {
            throw new BadRequestResponse("latitude and longitude must be set together");
        }
        if (update.latitude() != null
                && (update.latitude().compareTo(LAT_MIN) < 0
                        || update.latitude().compareTo(LAT_MAX) > 0)) {
            throw new BadRequestResponse("latitude out of range [-90, 90]");
        }
        if (update.longitude() != null
                && (update.longitude().compareTo(LON_MIN) < 0
                        || update.longitude().compareTo(LON_MAX) > 0)) {
            throw new BadRequestResponse("longitude out of range [-180, 180]");
        }
        if (update.country() != null
                && !update.country().isBlank()
                && !COUNTRY_RX.matcher(update.country().trim()).matches()) {
            throw new BadRequestResponse("country must be ISO-3166-1 alpha-2 uppercase");
        }
        rejectIfTooLong("addressLine", update.addressLine());
        rejectIfTooLong("postalCode", update.postalCode());
        rejectIfTooLong("city", update.city());
    }

    private static void rejectIfTooLong(String field, String value) {
        if (value != null && value.length() > MAX_TEXT_LEN) {
            throw new BadRequestResponse(field + " exceeds " + MAX_TEXT_LEN + " characters");
        }
    }

    private static String trimToNull(String value) {
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
                .orElseThrow(() -> new BadRequestResponse("Station not found"));
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
    }

    /**
     * Convenience: clear every column in one call. Used by the "Standort entfernen" button.
     */
    public void clear(int stationId) {
        repository.updateLocation(stationId, null, null, null, null, null, null);
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
            String addressLine,
            String postalCode,
            String city,
            String country,
            BigDecimal latitude,
            BigDecimal longitude) {}

    /**
     * Request payload for {@code PUT /station/location}.
     */
    public record LocationUpdate(
            String addressLine,
            String postalCode,
            String city,
            String country,
            BigDecimal latitude,
            BigDecimal longitude) {}
}
