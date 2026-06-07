/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.federation.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;
import de.chojo.sadu.queries.converter.StandardValueConverter;

import java.time.Instant;
import java.util.UUID;

import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

public record FederationPartner(
        int id,
        int stationId,
        UUID partnerStationId,
        String inviteCode,
        String publicKey,
        String partnerPublicKey,
        FederationStatus status,
        String federationVersion,
        Instant createdAt,
        Instant updatedAt,
        String remoteHost) {

    public enum FederationStatus {
        PENDING,
        ACTIVE,
        SUSPENDED
    }

    /**
     * Returns true if this partner is on a remote instance (requires HTTP communication).
     * A null remoteHost means the partner is on the same instance.
     */
    public boolean isRemote() {
        return remoteHost != null;
    }

    public static RowMapping<FederationPartner> map() {
        return row -> new FederationPartner(
                row.getInt("id"),
                row.getInt("station_id"),
                row.get("partner_station_id", StandardValueConverter.UUID_STRING),
                row.getString("invite_code"),
                row.getString("public_key"),
                row.getString("partner_public_key"),
                FederationStatus.valueOf(row.getString("status")),
                row.getString("federation_version"),
                row.get("created_at", INSTANT_TIMESTAMP),
                row.get("updated_at", INSTANT_TIMESTAMP),
                row.getString("remote_host"));
    }
}
