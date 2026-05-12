/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

public record RegistrationCode(int id, int stationId, String code, int maxUses, int uses) {

    public boolean hasUsesLeft() {
        return maxUses == -1 || uses < maxUses;
    }

    public static RowMapping<RegistrationCode> map() {
        return row -> new RegistrationCode(
                row.getInt("id"),
                row.getInt("station_id"),
                row.getString("code"),
                row.getInt("max_uses"),
                row.getInt("uses"));
    }
}
