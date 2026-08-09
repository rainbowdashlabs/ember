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
        FederationContract federationContract,
        Instant createdAt,
        Instant updatedAt,
        String remoteHost,
        String partnerStationName) {

    public static RowMapping<FederationPartner> map() {
        return row -> new FederationPartner(
                row.getInt("id"),
                row.getInt("station_id"),
                row.get("partner_station_id", StandardValueConverter.UUID_STRING),
                row.getString("invite_code"),
                row.getString("public_key"),
                row.getString("partner_public_key"),
                row.getEnum("status", FederationStatus.class),
                FederationContract.fromJson(row.getString("federation_contract")),
                row.get("created_at", INSTANT_TIMESTAMP),
                row.get("updated_at", INSTANT_TIMESTAMP),
                row.getString("remote_host"),
                row.getString("partner_station_name"));
    }

    /**
     * Returns true if this partner is on a remote instance (requires HTTP communication).
     * A null remoteHost means the partner is on the same instance.
     */
    public boolean isRemote() {
        return remoteHost != null;
    }

    /**
     * The core hash of the partner's last presented contract vector, or {@code null}
     * while the vector is unknown.
     */
    public String coreHash() {
        return federationContract != null ? federationContract.core() : null;
    }

    public enum FederationStatus {
        PENDING,
        ACTIVE,
        SUSPENDED
    }
}
