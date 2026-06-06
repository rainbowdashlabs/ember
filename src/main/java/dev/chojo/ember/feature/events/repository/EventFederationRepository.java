/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.repository;

import de.chojo.sadu.queries.api.call.Call;
import de.chojo.sadu.queries.api.query.Query;
import de.chojo.sadu.queries.converter.StandardValueConverter;
import dev.chojo.ember.feature.events.entity.EventFederationRegistration;
import dev.chojo.ember.feature.events.entity.EventFederationShare;
import jakarta.inject.Singleton;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for managing federated event sharing, registrations, and member name caching.
 */
@Singleton
public class EventFederationRepository {

    // -- Share management --

    /**
     * Finds the federation share configuration for an event.
     *
     * @param eventId the event ID
     * @return the share, if configured
     */
    public Optional<EventFederationShare> findShareByEvent(int eventId) {
        return Query.query("SELECT id, event_id, scope FROM event_federation_share WHERE event_id = :event_id;")
                .single(Call.of().bind("event_id", eventId))
                .map(EventFederationShare.map())
                .first();
    }

    /**
     * Creates or updates the federation share for an event.
     *
     * @param eventId the event ID
     * @param scope   the sharing scope
     * @return the created or updated share
     */
    public EventFederationShare setShare(int eventId, String scope) {
        return Query.query("""
                        INSERT INTO event_federation_share(event_id, scope)
                        VALUES (:event_id, :scope)
                        ON CONFLICT (event_id) DO UPDATE SET scope = :scope
                        RETURNING id, event_id, scope;""")
                .single(Call.of().bind("event_id", eventId).bind("scope", scope))
                .map(EventFederationShare.map())
                .first()
                .orElseThrow();
    }

    /**
     * Replaces all share targets for a given share by deleting existing ones and inserting the given partner IDs.
     *
     * @param shareId    the share ID
     * @param partnerIds the partner IDs to target
     */
    public void setShareTargets(int shareId, List<Integer> partnerIds) {
        Query.query("DELETE FROM event_federation_share_target WHERE share_id = :share_id;")
                .single(Call.of().bind("share_id", shareId))
                .delete();
        for (int partnerId : partnerIds) {
            Query.query(
                            "INSERT INTO event_federation_share_target(share_id, partner_id) VALUES (:share_id, :partner_id);")
                    .single(Call.of().bind("share_id", shareId).bind("partner_id", partnerId))
                    .insert();
        }
    }

    /**
     * Retrieves the partner IDs targeted by a share.
     *
     * @param shareId the share ID
     * @return the list of partner IDs
     */
    public List<Integer> findShareTargets(int shareId) {
        return Query.query("SELECT partner_id FROM event_federation_share_target WHERE share_id = :share_id;")
                .single(Call.of().bind("share_id", shareId))
                .map(row -> row.getInt("partner_id"))
                .all();
    }

    /**
     * Removes the federation share for an event. Cascades to share targets.
     *
     * @param eventId the event ID
     */
    public void removeShare(int eventId) {
        Query.query("DELETE FROM event_federation_share WHERE event_id = :event_id;")
                .single(Call.of().bind("event_id", eventId))
                .delete();
    }

    // -- Finding shared events for a partner --

    /**
     * Finds event IDs shared with a partner for a given station.
     * An event is shared if it has scope='ALL_PARTNERS', or scope='SPECIFIC' with the partner in targets.
     *
     * @param partnerId the federation partner ID
     * @param stationId the station ID
     * @return the list of shared event IDs
     */
    public List<Integer> findSharedEventIds(int partnerId, int stationId) {
        return Query.query("""
                        SELECT efs.event_id
                        FROM event_federation_share efs
                            JOIN station_event se ON se.id = efs.event_id
                        WHERE se.station_id = :station_id
                          AND (efs.scope = 'ALL_PARTNERS'
                               OR (efs.scope = 'SPECIFIC'
                                   AND EXISTS (SELECT 1 FROM event_federation_share_target efst
                                               WHERE efst.share_id = efs.id AND efst.partner_id = :partner_id)));""")
                .single(Call.of().bind("station_id", stationId).bind("partner_id", partnerId))
                .map(row -> row.getInt("event_id"))
                .all();
    }

    // -- Registration --

    /**
     * Creates a federated registration.
     *
     * @param eventId        the event ID
     * @param partnerId      the federation partner ID
     * @param remoteMemberId the remote member UUID
     * @param eventDate      the event occurrence date
     * @return the created registration
     */
    public EventFederationRegistration createRegistration(
            int eventId, int partnerId, UUID remoteMemberId, LocalDate eventDate) {
        return Query.query("""
                        INSERT INTO event_federation_registration(event_id, partner_id, remote_member_id, event_date)
                        VALUES (:event_id, :partner_id, :remote_member_id::uuid, :event_date)
                        RETURNING id, event_id, partner_id, remote_member_id, event_date, status, created_at;""")
                .single(Call.of()
                        .bind("event_id", eventId)
                        .bind("partner_id", partnerId)
                        .bind("remote_member_id", remoteMemberId, StandardValueConverter.UUID_STRING)
                        .bind("event_date", eventDate))
                .map(EventFederationRegistration.map())
                .first()
                .orElseThrow();
    }

    /**
     * Updates the status of a federated registration.
     *
     * @param id     the registration ID
     * @param status the new status
     * @return true if a row was updated
     */
    public boolean updateRegistrationStatus(int id, String status) {
        return Query.query("UPDATE event_federation_registration SET status = :status WHERE id = :id;")
                .single(Call.of().bind("status", status).bind("id", id))
                .update()
                .changed();
    }

    /**
     * Finds a federated registration by its ID.
     *
     * @param id the registration ID
     * @return the registration, if found
     */
    public Optional<EventFederationRegistration> findRegistrationById(int id) {
        return Query.query(
                        "SELECT id, event_id, partner_id, remote_member_id, event_date, status, created_at FROM event_federation_registration WHERE id = :id;")
                .single(Call.of().bind("id", id))
                .map(EventFederationRegistration.map())
                .first();
    }

    /**
     * Finds all federated registrations for an event on a specific date.
     *
     * @param eventId   the event ID
     * @param eventDate the event occurrence date
     * @return the list of registrations
     */
    public List<EventFederationRegistration> findRegistrations(int eventId, LocalDate eventDate) {
        if (eventDate == null) {
            return Query.query(
                            "SELECT id, event_id, partner_id, remote_member_id, event_date, status, created_at FROM event_federation_registration WHERE event_id = :event_id ORDER BY created_at;")
                    .single(Call.of().bind("event_id", eventId))
                    .map(EventFederationRegistration.map())
                    .all();
        }
        return Query.query(
                        "SELECT id, event_id, partner_id, remote_member_id, event_date, status, created_at FROM event_federation_registration WHERE event_id = :event_id AND event_date = :event_date ORDER BY created_at;")
                .single(Call.of().bind("event_id", eventId).bind("event_date", eventDate))
                .map(EventFederationRegistration.map())
                .all();
    }

    /**
     * Finds all federated registrations by partner.
     *
     * @param partnerId the federation partner ID
     * @return the list of registrations
     */
    public List<EventFederationRegistration> findRegistrationsByRemoteMember(UUID remoteMemberId) {
        return Query.query(
                        "SELECT id, event_id, partner_id, remote_member_id, event_date, status, created_at FROM event_federation_registration WHERE remote_member_id = :remote_member_id::uuid ORDER BY event_date;")
                .single(Call.of().bind("remote_member_id", remoteMemberId, StandardValueConverter.UUID_STRING))
                .map(EventFederationRegistration.map())
                .all();
    }

    public List<EventFederationRegistration> findRegistrationsByPartner(int partnerId) {
        return Query.query(
                        "SELECT id, event_id, partner_id, remote_member_id, event_date, status, created_at FROM event_federation_registration WHERE partner_id = :partner_id ORDER BY event_date;")
                .single(Call.of().bind("partner_id", partnerId))
                .map(EventFederationRegistration.map())
                .all();
    }

    /**
     * Deletes a federated registration by its composite key.
     *
     * @param eventId        the event ID
     * @param partnerId      the federation partner ID
     * @param remoteMemberId the remote member UUID
     * @param eventDate      the event occurrence date
     * @return true if a row was deleted
     */
    public boolean deleteRegistration(int eventId, int partnerId, UUID remoteMemberId, LocalDate eventDate) {
        return Query.query("""
                        DELETE FROM event_federation_registration
                        WHERE event_id = :event_id AND partner_id = :partner_id
                          AND remote_member_id = :remote_member_id::uuid AND event_date = :event_date;""")
                .single(Call.of()
                        .bind("event_id", eventId)
                        .bind("partner_id", partnerId)
                        .bind("remote_member_id", remoteMemberId, StandardValueConverter.UUID_STRING)
                        .bind("event_date", eventDate))
                .delete()
                .changed();
    }

    // -- Name cache --

    /**
     * Caches the display name for a federated member.
     *
     * @param partnerId      the federation partner ID
     * @param remoteMemberId the remote member UUID
     * @param displayName    the display name to cache
     */
    public void cacheName(int partnerId, UUID remoteMemberId, String displayName) {
        Query.query("""
                        INSERT INTO federation_member_name_cache(partner_id, remote_member_id, display_name)
                        VALUES (:partner_id, :remote_member_id::uuid, :display_name)
                        ON CONFLICT (partner_id, remote_member_id) DO UPDATE SET display_name = :display_name, cached_at = now();""")
                .single(Call.of()
                        .bind("partner_id", partnerId)
                        .bind("remote_member_id", remoteMemberId, StandardValueConverter.UUID_STRING)
                        .bind("display_name", displayName))
                .insert();
    }

    /**
     * Retrieves the cached display name for a federated member.
     *
     * @param partnerId      the federation partner ID
     * @param remoteMemberId the remote member UUID
     * @return the display name, if cached
     */
    public Optional<String> getCachedName(int partnerId, UUID remoteMemberId) {
        return Query.query(
                        "SELECT display_name FROM federation_member_name_cache WHERE partner_id = :partner_id AND remote_member_id = :remote_member_id::uuid;")
                .single(Call.of()
                        .bind("partner_id", partnerId)
                        .bind("remote_member_id", remoteMemberId, StandardValueConverter.UUID_STRING))
                .map(row -> row.getString("display_name"))
                .first();
    }

    /**
     * Invalidates the cached name for a federated member.
     *
     * @param partnerId      the federation partner ID
     * @param remoteMemberId the remote member UUID
     */
    public void invalidateName(int partnerId, UUID remoteMemberId) {
        Query.query(
                        "DELETE FROM federation_member_name_cache WHERE partner_id = :partner_id AND remote_member_id = :remote_member_id::uuid;")
                .single(Call.of()
                        .bind("partner_id", partnerId)
                        .bind("remote_member_id", remoteMemberId, StandardValueConverter.UUID_STRING))
                .delete();
    }
}
