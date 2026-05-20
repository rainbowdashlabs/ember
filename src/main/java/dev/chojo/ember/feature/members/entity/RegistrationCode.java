/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

/**
 * A registration code that allows new members to join a station.
 *
 * @param id        the code identifier
 * @param stationId the station this code belongs to
 * @param code      the registration code string
 * @param maxUses   the maximum number of times this code can be used (-1 for unlimited)
 * @param uses      the current number of times this code has been used
 */
public record RegistrationCode(int id, int stationId, String code, int maxUses, int uses) {

    /**
     * Creates a row mapping for database result set conversion.
     */
    public static RowMapping<RegistrationCode> map() {
        return row -> new RegistrationCode(
                row.getInt("id"),
                row.getInt("station_id"),
                row.getString("code"),
                row.getInt("max_uses"),
                row.getInt("uses"));
    }

    /**
     * Checks whether this registration code can still be used.
     *
     * @return true if the code has unlimited uses or has not reached its maximum
     */
    public boolean hasUsesLeft() {
        return maxUses == -1 || uses < maxUses;
    }
}
