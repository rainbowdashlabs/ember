/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.federation.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

public record FederationCapability(int id, int partnerId, String capability, String direction, boolean enabled) {

    public enum CapabilityType {
        KB_SHARE,
        QUIZ_SHARE,
        PROTOCOL_SHARE,
        INVENTORY_LEND,
        EVENT_SHARE
    }

    public enum Direction {
        IMPORT,
        EXPORT
    }

    public static RowMapping<FederationCapability> map() {
        return row -> new FederationCapability(
                row.getInt("id"),
                row.getInt("partner_id"),
                row.getString("capability"),
                row.getString("direction"),
                row.getBoolean("enabled"));
    }
}
