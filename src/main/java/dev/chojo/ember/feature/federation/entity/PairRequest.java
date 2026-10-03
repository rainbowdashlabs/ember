/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.federation.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;
import static de.chojo.sadu.queries.converter.StandardValueConverter.UUID_STRING;

/**
 * A request to federate between a station of this instance and a station of another instance.
 *
 * <p>Each instance keeps its own row: the asked one an {@link PairRequestDirection#INCOMING} row, the
 * asking one an {@link PairRequestDirection#OUTGOING} row. The other station is named only by what its
 * instance sent, since nothing about it is in this database.
 *
 * @param id                the request
 * @param stationId         the station of this instance
 * @param direction         whether the other station asked or was asked
 * @param remoteStationUid  the station of the other instance
 * @param remoteStationName its name as sent
 * @param remoteBaseUrl     where the other instance is reached
 * @param remoteInstanceKey the discovery key of the other instance, which signs everything about this request
 * @param remotePublicKey   the federation key of the other station, where it is known
 * @param remoteContract    the federation contract the other instance spoke, where it is known
 * @param status            whether and how the request was answered
 * @param createdAt         when it was sent or received
 * @param answeredAt        when it was answered, or {@code null} while pending
 * @param checkedAt         when this instance last asked the other one about it, or {@code null}
 */
public record PairRequest(
        int id,
        int stationId,
        PairRequestDirection direction,
        UUID remoteStationUid,
        String remoteStationName,
        String remoteBaseUrl,
        String remoteInstanceKey,
        @Nullable String remotePublicKey,
        @Nullable FederationContract remoteContract,
        PairRequestStatus status,
        Instant createdAt,
        @Nullable Instant answeredAt,
        @Nullable Instant checkedAt) {

    /** How long a decline keeps the same station from asking again. */
    public static final Duration DECLINE_COOLDOWN = Duration.ofDays(30);

    public static RowMapping<PairRequest> map() {
        return row -> new PairRequest(
                row.getInt("id"),
                row.getInt("station_id"),
                row.getEnum("direction", PairRequestDirection.class),
                row.get("remote_station_uid", UUID_STRING),
                row.getString("remote_station_name"),
                row.getString("remote_base_url"),
                row.getString("remote_instance_key"),
                row.getString("remote_public_key"),
                FederationContract.fromJson(row.getString("remote_contract")),
                row.getEnum("status", PairRequestStatus.class),
                row.get("created_at", INSTANT_TIMESTAMP),
                row.get("answered_at", INSTANT_TIMESTAMP),
                row.get("checked_at", INSTANT_TIMESTAMP));
    }

    /**
     * Whether a decline of this request still keeps the same station from asking again.
     *
     * @param now the moment asked about
     * @return true for a decline less than {@link #DECLINE_COOLDOWN} ago
     */
    public boolean coolingDown(Instant now) {
        Instant answered = answeredAt;
        return status == PairRequestStatus.DECLINED
                && answered != null
                && answered.plus(DECLINE_COOLDOWN).isAfter(now);
    }

    /**
     * The federation key of the other station, which an incoming request always carries.
     *
     * @return the key
     * @throws IllegalStateException for an outgoing request not yet accepted
     */
    public String requireRemotePublicKey() {
        String key = remotePublicKey;
        if (key == null) throw new IllegalStateException("Pair request " + id + " carries no key yet");
        return key;
    }
}
