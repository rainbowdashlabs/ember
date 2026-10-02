/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.federation.repository;

import dev.chojo.ember.feature.federation.entity.FederationContract;
import dev.chojo.ember.feature.federation.entity.PairRequest;
import dev.chojo.ember.feature.federation.entity.PairRequestDirection;
import dev.chojo.ember.feature.federation.entity.PairRequestStatus;
import dev.chojo.ember.util.sql.SqlSupport;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;
import static de.chojo.sadu.queries.converter.StandardValueConverter.UUID_STRING;
import static dev.chojo.ember.util.sql.SqlSupport.deleteById;
import static dev.chojo.ember.util.sql.SqlSupport.insertReturning;

/**
 * The requests to federate between a station here and a station of another instance, both the ones
 * stations here were asked and the ones they sent.
 */
@Singleton
public class PairRequestRepository {
    private static final String TABLE = "federation_pair_request";
    private static final String COLUMNS = """
            id, station_id, direction, remote_station_uid, remote_station_name, remote_base_url, \
            remote_instance_key, remote_public_key, remote_contract, status, created_at, answered_at, \
            checked_at""";

    /**
     * One request by its id.
     *
     * @param id the request
     * @return the request, or empty when there is none
     */
    public Optional<PairRequest> findById(int id) {
        return SqlSupport.findById(TABLE, COLUMNS, id, PairRequest.map());
    }

    /**
     * The request a station here has with a station of another instance in one direction.
     *
     * @param stationId        the station here
     * @param direction        which side the station here is on
     * @param remoteStationUid the station of the other instance
     * @return the request, or empty when the two have none in that direction
     */
    public Optional<PairRequest> find(int stationId, PairRequestDirection direction, UUID remoteStationUid) {
        return query("""
                SELECT %s
                FROM federation_pair_request
                WHERE station_id = :station_id
                  AND direction = :direction
                  AND remote_station_uid = :remote_station_uid::UUID;""", COLUMNS)
                .single(call().bind("station_id", stationId)
                        .bind("direction", direction)
                        .bind("remote_station_uid", remoteStationUid, UUID_STRING))
                .map(PairRequest.map())
                .first();
    }

    /**
     * Records a request a station of another instance sent to a station here. A request the same
     * station sent before, answered or not, is replaced and starts over as pending.
     *
     * @param stationId         the asked station here
     * @param remoteStationUid  the asking station
     * @param remoteStationName its name as sent
     * @param remoteBaseUrl     where its instance is reached
     * @param remoteInstanceKey its instance's discovery key
     * @param remotePublicKey   its federation key
     * @param remoteContract    the contract its instance speaks
     * @return the pending request
     */
    public PairRequest recordIncoming(
            int stationId,
            UUID remoteStationUid,
            String remoteStationName,
            String remoteBaseUrl,
            String remoteInstanceKey,
            String remotePublicKey,
            FederationContract remoteContract) {
        return record(
                stationId,
                PairRequestDirection.INCOMING,
                remoteStationUid,
                remoteStationName,
                remoteBaseUrl,
                remoteInstanceKey,
                remotePublicKey,
                remoteContract);
    }

    /**
     * Records a request a station here sent to a station of another instance. A request sent before
     * is replaced and starts over as pending.
     *
     * @param stationId         the asking station here
     * @param remoteStationUid  the asked station
     * @param remoteStationName its name
     * @param remoteBaseUrl     where its instance is reached
     * @param remoteInstanceKey its instance's discovery key
     * @return the pending request
     */
    public PairRequest recordOutgoing(
            int stationId,
            UUID remoteStationUid,
            String remoteStationName,
            String remoteBaseUrl,
            String remoteInstanceKey) {
        return record(
                stationId,
                PairRequestDirection.OUTGOING,
                remoteStationUid,
                remoteStationName,
                remoteBaseUrl,
                remoteInstanceKey,
                null,
                null);
    }

    private PairRequest record(
            int stationId,
            PairRequestDirection direction,
            UUID remoteStationUid,
            String remoteStationName,
            String remoteBaseUrl,
            String remoteInstanceKey,
            @Nullable String remotePublicKey,
            @Nullable FederationContract remoteContract) {
        return insertReturning(
                """
                INSERT INTO federation_pair_request(station_id, direction, remote_station_uid, remote_station_name,
                                                    remote_base_url, remote_instance_key, remote_public_key,
                                                    remote_contract)
                VALUES (:station_id, :direction, :remote_station_uid::UUID, :remote_station_name, :remote_base_url,
                        :remote_instance_key, :remote_public_key, :remote_contract::JSONB)
                ON CONFLICT (station_id, direction, remote_station_uid) DO UPDATE
                SET remote_station_name = excluded.remote_station_name,
                    remote_base_url     = excluded.remote_base_url,
                    remote_instance_key = excluded.remote_instance_key,
                    remote_public_key   = excluded.remote_public_key,
                    remote_contract     = excluded.remote_contract,
                    status              = 'PENDING',
                    created_at          = now(),
                    answered_at         = NULL,
                    checked_at          = NULL
                RETURNING %s;""",
                call().bind("station_id", stationId)
                        .bind("direction", direction)
                        .bind("remote_station_uid", remoteStationUid, UUID_STRING)
                        .bind("remote_station_name", remoteStationName)
                        .bind("remote_base_url", remoteBaseUrl)
                        .bind("remote_instance_key", remoteInstanceKey)
                        .bind("remote_public_key", remotePublicKey)
                        .bind("remote_contract", remoteContract == null ? null : remoteContract.toJson()),
                PairRequest.map(),
                COLUMNS);
    }

    /**
     * Answers a pending request. A request answered already keeps its first answer.
     *
     * @param id     the request
     * @param status the answer
     * @return true when this call answered it
     */
    public boolean answer(int id, PairRequestStatus status) {
        return query("""
                UPDATE federation_pair_request
                SET status = :status, answered_at = now()
                WHERE id = :id AND status = 'PENDING';""")
                .single(call().bind("id", id).bind("status", status))
                .update()
                .changed();
    }

    /**
     * Notes that this instance has just asked the other instance about an outgoing request.
     *
     * @param id the request
     */
    public void markChecked(int id) {
        query("UPDATE federation_pair_request SET checked_at = now() WHERE id = :id;")
                .single(call().bind("id", id))
                .update();
    }

    /**
     * Removes a request, once it has turned into a partnership.
     *
     * @param id the request
     * @return true when there was one
     */
    public boolean delete(int id) {
        return deleteById(TABLE, id);
    }

    /**
     * The requests waiting for a station here to answer, oldest first.
     *
     * @param stationId the asked station
     * @return the pending incoming requests
     */
    public List<PairRequest> findPendingIncoming(int stationId) {
        return query("""
                SELECT %s
                FROM federation_pair_request
                WHERE station_id = :station_id AND direction = 'INCOMING' AND status = 'PENDING'
                ORDER BY created_at;""", COLUMNS)
                .single(call().bind("station_id", stationId))
                .map(PairRequest.map())
                .all();
    }

    /**
     * What a station here sent to other instances: everything still pending, and what was answered
     * since the given moment, newest first.
     *
     * @param stationId     the asking station
     * @param answeredSince the oldest answer still worth showing
     * @return the outgoing requests
     */
    public List<PairRequest> findOutgoing(int stationId, Instant answeredSince) {
        return query("""
                SELECT %s
                FROM federation_pair_request
                WHERE station_id = :station_id
                  AND direction = 'OUTGOING'
                  AND (status = 'PENDING' OR answered_at >= :since)
                ORDER BY created_at DESC;""", COLUMNS)
                .single(call().bind("station_id", stationId).bind("since", answeredSince, INSTANT_TIMESTAMP))
                .map(PairRequest.map())
                .all();
    }

    /**
     * The outgoing requests of every station that still wait for an answer and were last asked about
     * before the given moment, or never.
     *
     * @param checkedBefore the moment a request counts as due again
     * @return the due pending outgoing requests, the longest unchecked first
     */
    public List<PairRequest> findPendingOutgoingDue(Instant checkedBefore) {
        return query("""
                SELECT %s
                FROM federation_pair_request
                WHERE direction = 'OUTGOING'
                  AND status = 'PENDING'
                  AND (checked_at IS NULL OR checked_at < :before)
                ORDER BY checked_at NULLS FIRST;""", COLUMNS)
                .single(call().bind("before", checkedBefore, INSTANT_TIMESTAMP))
                .map(PairRequest.map())
                .all();
    }
}
