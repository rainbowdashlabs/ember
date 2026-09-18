/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.repository;

import de.chojo.sadu.queries.converter.StandardValueConverter;
import dev.chojo.ember.feature.events.entity.EventFederationRegistration;
import dev.chojo.ember.feature.events.entity.EventFederationShare;
import dev.chojo.ember.feature.events.entity.EventPartnerPlaces;
import dev.chojo.ember.feature.events.entity.RegistrationStatus;
import dev.chojo.ember.feature.federation.entity.ShareScope;
import dev.chojo.ember.feature.restriction.RestrictionSql;
import dev.chojo.ember.feature.restriction.RestrictionType;
import dev.chojo.ember.util.sql.SqlSupport;
import dev.chojo.ember.util.sql.Transactions;
import jakarta.inject.Singleton;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;

/**
 * Repository for managing federated event sharing, registrations, and member name caching.
 */
@Singleton
public class EventFederationRepository {
    private static final String EVENT_FEDERATION_SHARE_COLUMNS = "id, event_id, scope";
    private static final String EVENT_FEDERATION_REGISTRATION_COLUMNS =
            "id, event_id, partner_id, remote_member_id, event_date, status, created_at";

    /**
     * Finds the federation share configuration for an event.
     *
     * @param eventId the event ID
     * @return the share, if configured
     */
    public Optional<EventFederationShare> findShareByEvent(int eventId) {
        return query("""
                SELECT %s FROM event_federation_share WHERE event_id = :event_id;""", EVENT_FEDERATION_SHARE_COLUMNS)
                .single(call().bind("event_id", eventId))
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
    public EventFederationShare setShare(int eventId, ShareScope scope) {
        return SqlSupport.insertReturning(
                """
                INSERT INTO event_federation_share(event_id, scope)
                VALUES (:event_id, :scope)
                ON CONFLICT (event_id) DO UPDATE SET scope = :scope
                RETURNING %s;""",
                call().bind("event_id", eventId).bind("scope", scope),
                EventFederationShare.map(),
                EVENT_FEDERATION_SHARE_COLUMNS);
    }

    /**
     * Replaces all share targets for a given share by deleting existing ones and inserting the given partner IDs.
     *
     * @param shareId    the share ID
     * @param partnerIds the partner IDs to target
     */
    public void setShareTargets(int shareId, List<Integer> partnerIds) {
        query("DELETE FROM event_federation_share_target WHERE share_id = :share_id;")
                .single(call().bind("share_id", shareId))
                .delete();
        for (int partnerId : partnerIds) {
            query("""
                    INSERT
                    INTO
                        event_federation_share_target(share_id, partner_id)
                    VALUES
                        (:share_id, :partner_id);""")
                    .single(call().bind("share_id", shareId).bind("partner_id", partnerId))
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
        return query("SELECT partner_id FROM event_federation_share_target WHERE share_id = :share_id;")
                .single(call().bind("share_id", shareId))
                .map(row -> row.getInt("partner_id"))
                .all();
    }

    /**
     * Removes the federation share for an event. Cascades to share targets.
     *
     * @param eventId the event ID
     */
    public void removeShare(int eventId) {
        query("DELETE FROM event_federation_share WHERE event_id = :event_id;")
                .single(call().bind("event_id", eventId))
                .delete();
    }

    /**
     * Finds event IDs shared with a partner for a given station.
     * An event is shared if it has scope='ALL_PARTNERS', or scope='SPECIFIC' with the partner in targets.
     *
     * <p>An event that not every member of the owning station may know about is never among them,
     * whatever the share says. Deciding it here rather than at each caller also settles the order the
     * two settings were made in: restricting an event that was already shared withdraws it.
     *
     * @param partnerId the federation partner ID
     * @param stationId the station ID
     * @return the list of shared event IDs
     */
    public List<Integer> findSharedEventIds(int partnerId, int stationId) {
        return query("""
                SELECT efs.event_id
                FROM event_federation_share efs
                    JOIN station_event se ON se.id = efs.event_id
                WHERE se.station_id = :station_id
                  AND %s
                  AND (efs.scope = 'ALL_PARTNERS'
                       OR (efs.scope = 'SPECIFIC'
                           AND exists (SELECT 1 FROM event_federation_share_target efst
                                       WHERE efst.share_id = efs.id AND efst.partner_id = :partner_id)));""", RestrictionSql.unrestricted(RestrictionType.EVENT_VIEW, "se.id"))
                .single(call().bind("station_id", stationId).bind("partner_id", partnerId))
                .map(row -> row.getInt("event_id"))
                .all();
    }

    /**
     * Creates a federated registration, or answers again where one already stands.
     *
     * <p>Somebody may register, withdraw and register again, and once a withdrawal keeps its row the
     * second registration meets the unique key the first one wrote. Answering again is the same
     * gesture as answering the first time, so it takes the same door rather than a refusal the member
     * did nothing to earn.
     *
     * @param eventId        the event ID
     * @param partnerId      the federation partner ID
     * @param remoteMemberId the remote member UUID
     * @param eventDate      the event occurrence date
     * @param status         what the event's own rules make of the answer
     * @return the registration as it now stands
     */
    public EventFederationRegistration createRegistration(
            int eventId, int partnerId, UUID remoteMemberId, LocalDate eventDate, RegistrationStatus status) {
        return SqlSupport.insertReturning(
                """
                INSERT INTO event_federation_registration(event_id, partner_id, remote_member_id, event_date, status)
                VALUES (:event_id, :partner_id, :remote_member_id::UUID, :event_date, :status)
                ON CONFLICT (event_id, partner_id, remote_member_id, event_date)
                    DO UPDATE SET status            = EXCLUDED.status,
                                  created_at        = now(),
                                  previous_status   = event_federation_registration.status,
                                  status_changed_at = now()
                RETURNING %s;""",
                call().bind("event_id", eventId)
                        .bind("partner_id", partnerId)
                        .bind("remote_member_id", remoteMemberId, StandardValueConverter.UUID_STRING)
                        .bind("event_date", eventDate)
                        .bind("status", status),
                EventFederationRegistration.map(),
                EVENT_FEDERATION_REGISTRATION_COLUMNS);
    }

    /**
     * Updates the status of a federated registration.
     *
     * @param id     the registration ID
     * @param status the new status
     * @return true if a row was updated
     */
    public boolean updateRegistrationStatus(int id, RegistrationStatus status) {
        return query("UPDATE event_federation_registration SET status = :status WHERE id = :id;")
                .single(call().bind("status", status).bind("id", id))
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
        return SqlSupport.findById(
                "event_federation_registration",
                EVENT_FEDERATION_REGISTRATION_COLUMNS,
                id,
                EventFederationRegistration.map());
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
            return query("""
                    SELECT %s
                    FROM event_federation_registration
                    WHERE event_id = :event_id
                    ORDER BY created_at;""", EVENT_FEDERATION_REGISTRATION_COLUMNS)
                    .single(call().bind("event_id", eventId))
                    .map(EventFederationRegistration.map())
                    .all();
        }
        return query("""
                SELECT %s
                FROM event_federation_registration
                WHERE event_id = :event_id
                  AND event_date = :event_date
                ORDER BY created_at;""", EVENT_FEDERATION_REGISTRATION_COLUMNS)
                .single(call().bind("event_id", eventId).bind("event_date", eventDate))
                .map(EventFederationRegistration.map())
                .all();
    }

    /**
     * Finds all federated registrations by partner.
     *
     * @param remoteMemberId the federation partner ID
     * @return the list of registrations
     */
    public List<EventFederationRegistration> findRegistrationsByRemoteMember(UUID remoteMemberId) {
        return query("""
                SELECT %s
                FROM event_federation_registration
                WHERE remote_member_id = :remote_member_id::UUID
                ORDER BY event_date;""", EVENT_FEDERATION_REGISTRATION_COLUMNS)
                .single(call().bind("remote_member_id", remoteMemberId, StandardValueConverter.UUID_STRING))
                .map(EventFederationRegistration.map())
                .all();
    }

    public List<EventFederationRegistration> findRegistrationsByPartner(int partnerId) {
        return query("""
                SELECT %s
                FROM event_federation_registration
                WHERE partner_id = :partner_id
                ORDER BY event_date;""", EVENT_FEDERATION_REGISTRATION_COLUMNS)
                .single(call().bind("partner_id", partnerId))
                .map(EventFederationRegistration.map())
                .all();
    }

    /** One partner's member on one date, which is what the composite key addresses. */
    public Optional<EventFederationRegistration> findRegistration(
            int eventId, int partnerId, UUID remoteMemberId, LocalDate eventDate) {
        return query("""
                SELECT %s FROM event_federation_registration
                WHERE event_id = :event_id AND partner_id = :partner_id
                  AND remote_member_id = :remote_member_id::UUID AND event_date = :event_date;""", EVENT_FEDERATION_REGISTRATION_COLUMNS)
                .single(call().bind("event_id", eventId)
                        .bind("partner_id", partnerId)
                        .bind("remote_member_id", remoteMemberId, StandardValueConverter.UUID_STRING)
                        .bind("event_date", eventDate))
                .map(EventFederationRegistration.map())
                .first();
    }

    /**
     * What a partner may do with a shared appointment, or the arrangement that holds where nobody has
     * said: the host decides, with no cap.
     */
    public EventPartnerPlaces findPartnerPlaces(int eventId, int partnerId) {
        return query("""
                SELECT event_id, partner_id, slot_budget, partner_confirms
                FROM event_partner_places
                WHERE event_id = :event_id AND partner_id = :partner_id;""")
                .single(call().bind("event_id", eventId).bind("partner_id", partnerId))
                .map(EventPartnerPlaces.map())
                .first()
                .orElseGet(() -> EventPartnerPlaces.hostDecides(eventId, partnerId));
    }

    /** Everything said per partner about one appointment, for the screen that sets it. */
    public List<EventPartnerPlaces> findPartnerPlaces(int eventId) {
        return query("""
                SELECT event_id, partner_id, slot_budget, partner_confirms
                FROM event_partner_places
                WHERE event_id = :event_id;""")
                .single(call().bind("event_id", eventId))
                .map(EventPartnerPlaces.map())
                .all();
    }

    /**
     * Says what a partner may do, or stops saying anything where the host takes the decision back.
     *
     * <p>Taking it back removes the row rather than writing a false one, so an appointment nobody has
     * arranged anything for reads the same whether it never had an arrangement or lost one.
     */
    public void setPartnerPlaces(int eventId, int partnerId, Integer slotBudget, boolean partnerConfirms) {
        if (!partnerConfirms) {
            query("DELETE FROM event_partner_places WHERE event_id = :event_id AND partner_id = :partner_id;")
                    .single(call().bind("event_id", eventId).bind("partner_id", partnerId))
                    .delete();
            return;
        }
        query("""
                INSERT INTO event_partner_places(event_id, partner_id, slot_budget, partner_confirms)
                VALUES (:event_id, :partner_id, :slot_budget, TRUE)
                ON CONFLICT (event_id, partner_id)
                    DO UPDATE SET slot_budget = EXCLUDED.slot_budget, partner_confirms = TRUE;""")
                .single(call().bind("event_id", eventId)
                        .bind("partner_id", partnerId)
                        .bind("slot_budget", slotBudget))
                .insert();
    }

    /**
     * Fills one of a partner's places, and says whether there was one to fill.
     *
     * <p>Two people at the partner pressing confirm at the same moment must not both take the last
     * place, and putting the count inside the statement is not enough to stop them: each press
     * writes a different registration, so they lock different rows and each counts on a snapshot
     * taken before the other committed. Both would find room. So the arrangement itself is locked
     * first, which is the one row both presses have in common, and the count that follows runs after
     * whoever got there first has finished. Counted per date: the budget is per occurrence.
     *
     * @return true where the place was granted, false where the budget was already spent
     */
    public boolean acceptWithinBudget(int registrationId, int eventId, int partnerId, LocalDate eventDate) {
        return Transactions.call(() -> {
            lockPlaces(eventId, partnerId);
            return spendPlace(registrationId, eventId, partnerId, eventDate);
        });
    }

    /**
     * Holds the arrangement still while a place is counted and taken against it.
     *
     * <p>Nothing is read from it: what matters is that a second press waits here until the first has
     * committed, so that the count it then makes is a count of what is really taken. Where a partner
     * decides without a number there is no row and nothing to wait for, which is right, because
     * there is no limit to race for.
     */
    private void lockPlaces(int eventId, int partnerId) {
        query("SELECT 1 FROM event_partner_places WHERE event_id = :event_id AND partner_id = :partner_id FOR UPDATE;")
                .single(call().bind("event_id", eventId).bind("partner_id", partnerId))
                .map(row -> row.getInt(1))
                .first();
    }

    private boolean spendPlace(int registrationId, int eventId, int partnerId, LocalDate eventDate) {
        return query("""
                UPDATE event_federation_registration reg
                SET status = 'ACCEPTED', previous_status = reg.status, status_changed_at = now()
                WHERE reg.id = :id
                  AND reg.status <> 'ACCEPTED'
                  AND (
                      (SELECT slot_budget FROM event_partner_places
                       WHERE event_id = :event_id AND partner_id = :partner_id) IS NULL
                      OR (SELECT count(*) FROM event_federation_registration taken
                          WHERE taken.event_id = :event_id
                            AND taken.partner_id = :partner_id
                            AND taken.event_date = :event_date
                            AND taken.status = 'ACCEPTED')
                         < (SELECT slot_budget FROM event_partner_places
                            WHERE event_id = :event_id AND partner_id = :partner_id));""")
                .single(call().bind("id", registrationId)
                        .bind("event_id", eventId)
                        .bind("partner_id", partnerId)
                        .bind("event_date", eventDate))
                .update()
                .changed();
    }

    /** How many places a partner has actually filled on one date, for the screen that shows it. */
    public int countAcceptedForPartner(int eventId, int partnerId, LocalDate eventDate) {
        return query("""
                SELECT count(*) AS taken FROM event_federation_registration
                WHERE event_id = :event_id AND partner_id = :partner_id
                  AND event_date = :event_date AND status = 'ACCEPTED';""")
                .single(call().bind("event_id", eventId)
                        .bind("partner_id", partnerId)
                        .bind("event_date", eventDate))
                .map(row -> row.getInt("taken"))
                .first()
                .orElse(0);
    }

    /**
     * Records that a partner's member gave their place back.
     *
     * <p>The row stays where it used to be deleted. The host could not otherwise tell somebody who
     * withdrew from somebody who never answered, and a withdrawal with no row is a withdrawal nobody
     * can take back.
     *
     * @param eventId        the event ID
     * @param partnerId      the federation partner ID
     * @param remoteMemberId the remote member UUID
     * @param eventDate      the event occurrence date
     * @return true if a registration was withdrawn
     */
    public boolean withdrawRegistration(int eventId, int partnerId, UUID remoteMemberId, LocalDate eventDate) {
        return query("""
                UPDATE event_federation_registration
                SET status = 'WITHDRAWN', previous_status = status, status_changed_at = now()
                WHERE event_id = :event_id
                  AND partner_id = :partner_id
                  AND remote_member_id = :remote_member_id::UUID
                  AND event_date = :event_date
                  AND status <> 'WITHDRAWN';""")
                .single(call().bind("event_id", eventId)
                        .bind("partner_id", partnerId)
                        .bind("remote_member_id", remoteMemberId, StandardValueConverter.UUID_STRING)
                        .bind("event_date", eventDate))
                .update()
                .changed();
    }

    /**
     * Puts a partner's member back on the list, for as long as their withdrawal can be taken back.
     *
     * <p>The window is in the statement so two presses racing cannot both find it open, and it is
     * measured by this station's clock because this station holds the row. A partner whose own clock
     * disagrees still gets the answer the host gives.
     *
     * <p>Only a withdrawal goes back. Confirming and denying stamp what they wrote over as well, so
     * without this a partner could undo the host's deny, and a place the host had just given back to
     * the budget would be spent twice.
     *
     * @param window how long a withdrawal may be taken back
     * @return true where the place was restored, false where the window had closed
     */
    public boolean restoreRegistration(
            int eventId, int partnerId, UUID remoteMemberId, LocalDate eventDate, Duration window) {
        return query("""
                UPDATE event_federation_registration
                SET status = previous_status, previous_status = NULL, status_changed_at = now()
                WHERE event_id = :event_id
                  AND partner_id = :partner_id
                  AND remote_member_id = :remote_member_id::UUID
                  AND event_date = :event_date
                  AND status = 'WITHDRAWN'
                  AND previous_status IS NOT NULL
                  AND status_changed_at > now() - CAST(:window AS INTERVAL)
                  AND (
                      previous_status <> 'ACCEPTED'
                      OR (SELECT slot_budget FROM event_partner_places
                          WHERE event_id = :event_id AND partner_id = :partner_id) IS NULL
                      OR (SELECT count(*) FROM event_federation_registration taken
                          WHERE taken.event_id = :event_id
                            AND taken.partner_id = :partner_id
                            AND taken.event_date = :event_date
                            AND taken.status = 'ACCEPTED')
                         < (SELECT slot_budget FROM event_partner_places
                            WHERE event_id = :event_id AND partner_id = :partner_id));""")
                .single(call().bind("event_id", eventId)
                        .bind("partner_id", partnerId)
                        .bind("remote_member_id", remoteMemberId, StandardValueConverter.UUID_STRING)
                        .bind("event_date", eventDate)
                        .bind("window", window.toSeconds() + " seconds"))
                .update()
                .changed();
    }

    /**
     * Caches the display name for a federated member.
     *
     * @param partnerId      the federation partner ID
     * @param remoteMemberId the remote member UUID
     * @param displayName    the display name to cache
     */
    public void cacheName(int partnerId, UUID remoteMemberId, String displayName) {
        query("""
                INSERT
                INTO
                    federation_member_name_cache(partner_id, remote_member_id, display_name)
                VALUES
                    (:partner_id, :remote_member_id::UUID, :display_name)
                ON CONFLICT (partner_id, remote_member_id)
                    DO UPDATE
                    SET
                        display_name = :display_name,
                        cached_at    = now();""")
                .single(call().bind("partner_id", partnerId)
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
        return query("""
                SELECT
                    display_name
                FROM
                    federation_member_name_cache
                WHERE partner_id = :partner_id
                  AND remote_member_id = :remote_member_id::UUID;""")
                .single(call().bind("partner_id", partnerId)
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
        query("""
                DELETE
                FROM
                    federation_member_name_cache
                WHERE partner_id = :partner_id
                  AND remote_member_id = :remote_member_id::UUID;""")
                .single(call().bind("partner_id", partnerId)
                        .bind("remote_member_id", remoteMemberId, StandardValueConverter.UUID_STRING))
                .delete();
    }
}
