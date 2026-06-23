/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.storage.service;

import io.javalin.http.HttpResponseException;
import io.javalin.http.HttpStatus;

import java.util.Map;

/**
 * Thrown by {@link StorageService#store} when the supplied scope refers to a station that has
 * been flagged read-only for an in-flight cross-instance transfer. Maps to {@code 503 Service
 * Unavailable} so the caller (web or federation client) treats it as transient.
 */
public class StationReadOnlyForTransferException extends HttpResponseException {

    public StationReadOnlyForTransferException(int stationId) {
        super(
                HttpStatus.SERVICE_UNAVAILABLE.getCode(),
                "Station is being transferred to another instance",
                Map.of("stationId", String.valueOf(stationId)));
    }
}
