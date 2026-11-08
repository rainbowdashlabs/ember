/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.cluster.repository;

import dev.chojo.ember.feature.members.repository.FieldSetTables;
import jakarta.inject.Singleton;

/**
 * The bundles of an association's profile questions.
 */
@Singleton
public class ClusterFieldSetRepository extends FieldSetTables {
    public ClusterFieldSetRepository() {
        super("cluster_profile_field_set", "cluster_profile_field", "cluster_id");
    }
}
