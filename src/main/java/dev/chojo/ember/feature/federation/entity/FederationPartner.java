/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.federation.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;
import de.chojo.sadu.queries.converter.StandardValueConverter;
import dev.chojo.ember.util.WebOrigins;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;

import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

public record FederationPartner(
        int id,
        int stationId,
        UUID partnerStationId,
        @Nullable String inviteCode,
        @Nullable String publicKey,
        @Nullable String partnerPublicKey,
        FederationStatus status,
        @Nullable FederationContract federationContract,
        Instant createdAt,
        Instant updatedAt,
        @Nullable String remoteHost,
        @Nullable String partnerStationName,
        boolean clusterManaged,
        boolean clusterHome) {

    /**
     * A pair the two stations made themselves, which is every pair that is not a cluster's doing.
     */
    public FederationPartner(
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
        this(
                id,
                stationId,
                partnerStationId,
                inviteCode,
                publicKey,
                partnerPublicKey,
                status,
                federationContract,
                createdAt,
                updatedAt,
                remoteHost,
                partnerStationName,
                false,
                false);
    }

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
                row.getString("partner_station_name"),
                row.getBoolean("cluster_managed"),
                row.getBoolean("cluster_home"));
    }

    /**
     * Returns true if this partner is on a remote instance (requires HTTP communication).
     * A null remoteHost means the partner is on the same instance.
     */
    public boolean isRemote() {
        return remoteHost != null;
    }

    /**
     * Whether this row partners with the given station: the same identifier, on the given instance or,
     * where none is given, on this one.
     *
     * @param stationUid  the station's identifier
     * @param instanceUrl the address of the instance the station lives on, or {@code null} for this one
     * @return true when the row names that station on that instance
     */
    public boolean partnersWith(UUID stationUid, @Nullable String instanceUrl) {
        if (!stationUid.equals(partnerStationId)) return false;
        if (instanceUrl == null) return !isRemote();
        return WebOrigins.sameOrigin(remoteHost, instanceUrl);
    }

    /**
     * The host of a partner on another instance, for the callers that only ever talk to one.
     *
     * @return the host
     * @throws IllegalStateException for a partner on this instance, which has none
     */
    public String requireRemoteHost() {
        if (remoteHost == null) throw new IllegalStateException("Partner " + id + " is on this instance");
        return remoteHost;
    }

    /**
     * The core hash of the partner's last presented contract vector, or {@code null}
     * while the vector is unknown.
     */
    public @Nullable String coreHash() {
        return federationContract != null ? federationContract.core() : null;
    }

    /**
     * Whether the two stations may take this pair apart themselves.
     *
     * <p>A pair a cluster made is not theirs to end: it exists because both stations answer to the same
     * cluster, and it goes when that stops being true and not before.
     */
    public boolean deletableByStation() {
        return !clusterManaged;
    }

    /**
     * Whether either side may put this pair on hold.
     *
     * <p>A mesh pair between two member stations may be paused, because whether they want to see each other
     * is a matter between them. The pair carrying the cluster's own content may not: pausing it would leave
     * the station quietly missing what its cluster publishes, with nothing on screen saying why.
     */
    public boolean pausableByStation() {
        return !clusterHome;
    }

    public enum FederationStatus {
        PENDING,
        ACTIVE,
        SUSPENDED
    }
}
