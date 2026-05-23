/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.federation.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

import java.time.Instant;

import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

public record FederationPartner(
        int id,
        int stationId,
        int partnerStationId,
        String inviteCode,
        String publicKey,
        String partnerPublicKey,
        FederationStatus status,
        int federationVersion,
        Instant createdAt,
        Instant updatedAt) {

    public enum FederationStatus {
        PENDING,
        ACTIVE,
        SUSPENDED
    }

    public static RowMapping<FederationPartner> map() {
        return row -> new FederationPartner(
                row.getInt("id"),
                row.getInt("station_id"),
                row.getInt("partner_station_id"),
                row.getString("invite_code"),
                row.getString("public_key"),
                row.getString("partner_public_key"),
                FederationStatus.valueOf(row.getString("status")),
                row.getInt("federation_version"),
                row.get("created_at", INSTANT_TIMESTAMP),
                row.get("updated_at", INSTANT_TIMESTAMP));
    }
}
