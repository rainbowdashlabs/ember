/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.repository;

import jakarta.inject.Singleton;

/**
 * The bundles of a station's own profile questions.
 */
@Singleton
public class StationFieldSetRepository extends FieldSetTables {
    public StationFieldSetRepository() {
        super("profile_field_set", "profile_field", "station_id");
    }
}
