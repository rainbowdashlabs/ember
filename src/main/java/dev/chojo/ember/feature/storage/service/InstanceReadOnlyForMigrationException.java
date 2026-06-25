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
 * Thrown by {@link StorageService#store} when an instance-wide backend migration is in flight
 * (see {@link InstanceStorageReadOnlyState}). Maps to {@code 503 Service Unavailable} so HTTP
 * clients treat it as transient and retry after the migration finishes.
 */
public class InstanceReadOnlyForMigrationException extends HttpResponseException {
    public InstanceReadOnlyForMigrationException() {
        super(
                HttpStatus.SERVICE_UNAVAILABLE.getCode(),
                "Instance storage is being migrated; writes are temporarily paused",
                Map.of());
    }
}
