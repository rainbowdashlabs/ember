/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.federation.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;
import de.chojo.sadu.queries.converter.StandardValueConverter;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

/**
 * Represents an inventory lending request between two stations. The peer columns
 * ({@code requestingStationUid}, {@code owningStationUid}) carry the stations' stable UUIDs
 * rather than local integer ids, so a request still resolves correctly after one or both
 * stations have moved to a different Ember instance. A station is resolved local by
 * {@code SELECT 1 FROM station WHERE uid = ?}; if no row matches, the station lives on a remote
 * instance reached via {@code federation_partner}.
 *
 * <p>What the request is for travels as {@code occasion}, a copy of the appointment's name taken when
 * it was sent, and never as a link. Why a request is being made is the question that decides a yes,
 * and a title plus a window answers it; everything else on an appointment, the sign-ups, the fields
 * and the description, is no business of another station and may be restricted in the first place.
 * The appointment itself is named only at the requesting station, for counting what a need has.
 *
 * <p>A request between stations on two instances is kept on both of them, and the two copies know
 * each other by {@code uid}. Between two stations of one instance there is one row, which both read.
 *
 * @param eventId   the appointment at the requesting station, or {@code null}
 * @param eventDate the date of that appointment, or {@code null}
 * @param occasion  what the request is for, as the owning station reads it
 * @param uid       the identity the request carries between the two stations
 */
public record LendingRequest(
        int id,
        UUID requestingStationUid,
        UUID owningStationUid,
        LendingStatus status,
        LocalDate requestedDateFrom,
        @Nullable LocalDate requestedDateTo,
        @Nullable Integer createdBy,
        Instant createdAt,
        Instant updatedAt,
        @Nullable Integer eventId,
        @Nullable LocalDate eventDate,
        String occasion,
        UUID uid) {

    /**
     * A request whose identity between the stations does not matter to the caller.
     */
    public LendingRequest(
            int id,
            UUID requestingStationUid,
            UUID owningStationUid,
            LendingStatus status,
            LocalDate requestedDateFrom,
            LocalDate requestedDateTo,
            Integer createdBy,
            Instant createdAt,
            Instant updatedAt,
            Integer eventId,
            LocalDate eventDate,
            String occasion) {
        this(
                id,
                requestingStationUid,
                owningStationUid,
                status,
                requestedDateFrom,
                requestedDateTo,
                createdBy,
                createdAt,
                updatedAt,
                eventId,
                eventDate,
                occasion,
                null);
    }

    /**
     * Whether the given station is one of the two the request is between.
     *
     * @param stationUid the station
     * @return true for the borrowing and the lending station
     */
    public boolean isParty(UUID stationUid) {
        return requestingStationUid.equals(stationUid) || owningStationUid.equals(stationUid);
    }

    /**
     * The station on the other side of the request from the given one.
     *
     * @param stationUid one of the two stations
     * @return the other one
     */
    public UUID otherParty(UUID stationUid) {
        return requestingStationUid.equals(stationUid) ? owningStationUid : requestingStationUid;
    }

    public static RowMapping<LendingRequest> map() {
        return row -> new LendingRequest(
                row.getInt("id"),
                row.get("requesting_station_uid", StandardValueConverter.UUID_STRING),
                row.get("owning_station_uid", StandardValueConverter.UUID_STRING),
                row.getEnum("status", LendingStatus.class),
                row.getObject("requested_date_from", LocalDate.class),
                row.getObject("requested_date_to", LocalDate.class),
                row.getObject("created_by", Integer.class),
                row.get("created_at", INSTANT_TIMESTAMP),
                row.get("updated_at", INSTANT_TIMESTAMP),
                row.getObject("event_id", Integer.class),
                row.getObject("event_date", LocalDate.class),
                row.getString("occasion"),
                row.get("uid", StandardValueConverter.UUID_STRING));
    }
}
