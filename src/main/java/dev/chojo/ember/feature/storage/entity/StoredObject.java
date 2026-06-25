/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.storage.entity;

import dev.chojo.ember.feature.storage.backend.ObjectMetadata;

/**
 * Confirmation record returned by {@code StorageService.store(...)}. Carries the producer's
 * own key plus the metadata that survived the write (notably the SHA-256 computed during
 * streaming). Producers use the record to populate DB rows after a successful upload.
 */
public record StoredObject(
        StorageScope scope, StorageCategory category, String key, ObjectMetadata metadata, long contentLength) {}
