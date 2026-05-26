/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.federation.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

public record FederationCapability(
        int id, int partnerId, CapabilityType capability, Direction direction, boolean enabled) {

    public static RowMapping<FederationCapability> map() {
        return row -> new FederationCapability(
                row.getInt("id"),
                row.getInt("partner_id"),
                row.getEnum("capability", CapabilityType.class),
                row.getEnum("direction", Direction.class),
                row.getBoolean("enabled"));
    }
}
