/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.repository;

import de.chojo.sadu.queries.converter.StandardValueConverter;
import dev.chojo.ember.feature.events.entity.EventFederationRegistration;
import dev.chojo.ember.feature.events.entity.EventGroupPartnerRegistration;
import dev.chojo.ember.feature.events.entity.EventGroupShare;
import dev.chojo.ember.feature.events.entity.RegistrationStatus;
import dev.chojo.ember.feature.federation.entity.ShareScope;
import dev.chojo.ember.feature.restriction.RestrictionSql;
import dev.chojo.ember.feature.restriction.RestrictionType;
import dev.chojo.ember.util.sql.SqlSupport;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;

/**
 * Event groups shared with partner stations, and the sign-ups partners' members send for them.
 */
@Singleton
public class EventGroupPartnerRepository {

    /**
     * How an event group is shared, where it is.
     *
     * @param eventGroupId the event group
     * @return the share, empty where the event group is not shared
     */
    public Optional<EventGroupShare> findShare(int eventGroupId) {
        return query("SELECT scope FROM event_group_share WHERE event_group_id = :event_group_id;")
                .single(call().bind("event_group_id", eventGroupId))
                .map(row -> new EventGroupShare(
                        eventGroupId, row.getEnum("scope", ShareScope.class), targets(eventGroupId)))
                .first();
    }

    private List<Integer> targets(int eventGroupId) {
        return query(
                        "SELECT partner_id FROM event_group_share_target WHERE event_group_id = :event_group_id ORDER BY partner_id;")
                .single(call().bind("event_group_id", eventGroupId))
                .map(row -> row.getInt("partner_id"))
                .all();
    }

    /**
     * Shares an event group, replacing how it was shared before.
     *
     * @param eventGroupId the event group
     * @param scope        every partner, or the ones named
     * @param partnerIds   the partners named
     */
    public void setShare(int eventGroupId, ShareScope scope, List<Integer> partnerIds) {
        query("""
                INSERT INTO event_group_share(event_group_id, scope) VALUES (:event_group_id, :scope)
                ON CONFLICT (event_group_id) DO UPDATE SET scope = :scope;""")
                .single(call().bind("event_group_id", eventGroupId).bind("scope", scope))
                .insert();
        query("DELETE FROM event_group_share_target WHERE event_group_id = :event_group_id;")
                .single(call().bind("event_group_id", eventGroupId))
                .delete();
        for (int partnerId : partnerIds) {
            query(
                            "INSERT INTO event_group_share_target(event_group_id, partner_id) VALUES (:event_group_id, :partner_id);")
                    .single(call().bind("event_group_id", eventGroupId).bind("partner_id", partnerId))
                    .insert();
        }
    }

    /**
     * Stops sharing an event group.
     *
     * @param eventGroupId the event group
     */
    public void removeShare(int eventGroupId) {
        query("DELETE FROM event_group_share WHERE event_group_id = :event_group_id;")
                .single(call().bind("event_group_id", eventGroupId))
                .delete();
    }

    /**
     * Whether a station shares one of its event groups with a partner. An event group not every member of
     * the station may see is never shared, whatever its share says.
     *
     * @param eventGroupId the event group
     * @param partnerId    the station's record of the partner
     * @param stationId    the station
     * @return true where the partner may reach the event group
     */
    public boolean isSharedWith(int eventGroupId, int partnerId, int stationId) {
        return query("""
                SELECT EXISTS(SELECT 1
                              FROM event_group_share gs
                                  JOIN event_group g ON g.id = gs.event_group_id
                              WHERE g.id = :event_group_id
                                AND g.station_id = :station_id
                                AND %s
                                AND (gs.scope = 'ALL_PARTNERS'
                                     OR (gs.scope = 'SPECIFIC'
                                         AND exists (SELECT 1 FROM event_group_share_target t
                                                     WHERE t.event_group_id = gs.event_group_id
                                                       AND t.partner_id = :partner_id)))) AS shared;""", RestrictionSql.unrestricted(RestrictionType.EVENT_GROUP_VIEW, "g.id"))
                .single(call().bind("event_group_id", eventGroupId)
                        .bind("station_id", stationId)
                        .bind("partner_id", partnerId))
                .map(row -> row.getBoolean("shared"))
                .first()
                .orElse(false);
    }

    /**
     * How many of one partner's sign-ups for an event group stand at a status, which is what its places
     * are counted in: a sign-up holds one place for the whole group, whatever parts it chose.
     *
     * @param eventGroupId the event group
     * @param partnerId    the station's record of the partner
     * @param status       the status counted, such as ACCEPTED for the places taken
     * @return the number of the partner's sign-ups at that status
     */
    public int countForPartner(int eventGroupId, int partnerId, RegistrationStatus status) {
        return query("""
                SELECT count(*) AS counted FROM event_group_partner_registration
                WHERE event_group_id = :event_group_id AND partner_id = :partner_id AND status = :status;""")
                .single(call().bind("event_group_id", eventGroupId)
                        .bind("partner_id", partnerId)
                        .bind("status", status))
                .map(row -> row.getInt("counted"))
                .first()
                .orElse(0);
    }

    /**
     * A partner member's sign-up for an event group.
     *
     * @param eventGroupId   the event group
     * @param partnerId      the partner
     * @param remoteMemberId the member
     * @return the sign-up, empty where the member never signed up
     */
    public Optional<EventGroupPartnerRegistration> find(int eventGroupId, int partnerId, UUID remoteMemberId) {
        return query("""
                SELECT %s FROM event_group_partner_registration
                WHERE event_group_id = :event_group_id AND partner_id = :partner_id AND remote_member_id = :member::UUID;""", EventGroupPartnerRegistration.COLUMNS)
                .single(call().bind("event_group_id", eventGroupId)
                        .bind("partner_id", partnerId)
                        .bind("member", remoteMemberId, StandardValueConverter.UUID_STRING))
                .map(EventGroupPartnerRegistration.map())
                .first();
    }

    /**
     * A partner sign-up by its id.
     *
     * @param id the sign-up
     * @return the sign-up, empty where there is none
     */
    public Optional<EventGroupPartnerRegistration> findById(int id) {
        return query(
                        "SELECT %s FROM event_group_partner_registration WHERE id = :id;",
                        EventGroupPartnerRegistration.COLUMNS)
                .single(call().bind("id", id))
                .map(EventGroupPartnerRegistration.map())
                .first();
    }

    /**
     * Every partner sign-up of an event group, oldest first.
     *
     * @param eventGroupId the event group
     * @return its partner sign-ups
     */
    public List<EventGroupPartnerRegistration> findByEventGroup(int eventGroupId) {
        return query("""
                SELECT %s FROM event_group_partner_registration
                WHERE event_group_id = :event_group_id
                ORDER BY registered_at, id;""", EventGroupPartnerRegistration.COLUMNS)
                .single(call().bind("event_group_id", eventGroupId))
                .map(EventGroupPartnerRegistration.map())
                .all();
    }

    /**
     * Signs a partner's member up, or takes them back after they withdrew.
     *
     * @param eventGroupId   the event group
     * @param partnerId      the partner
     * @param remoteMemberId the member
     * @param status         the status the sign-up starts with
     * @return the sign-up
     */
    public EventGroupPartnerRegistration signUp(
            int eventGroupId, int partnerId, UUID remoteMemberId, RegistrationStatus status) {
        return SqlSupport.insertReturning(
                """
                INSERT INTO event_group_partner_registration(event_group_id, partner_id, remote_member_id, status)
                VALUES (:event_group_id, :partner_id, :member::UUID, :status)
                ON CONFLICT (event_group_id, partner_id, remote_member_id) DO UPDATE
                    SET status = :status, registered_at = now()
                RETURNING %s;""",
                call().bind("event_group_id", eventGroupId)
                        .bind("partner_id", partnerId)
                        .bind("member", remoteMemberId, StandardValueConverter.UUID_STRING)
                        .bind("status", status),
                EventGroupPartnerRegistration.map(),
                EventGroupPartnerRegistration.COLUMNS);
    }

    /**
     * Moves a partner sign-up to a new status.
     *
     * @param id     the sign-up
     * @param status the new status
     */
    public void updateStatus(int id, RegistrationStatus status) {
        query("UPDATE event_group_partner_registration SET status = :status WHERE id = :id;")
                .single(call().bind("status", status).bind("id", id))
                .update();
    }

    /**
     * Ties a partner registration for a part to the event group sign-up it was chosen with.
     *
     * @param registrationId                  the partner registration
     * @param eventGroupPartnerRegistrationId the partner's event group sign-up
     */
    public void link(int registrationId, int eventGroupPartnerRegistrationId) {
        query("""
                UPDATE event_federation_registration
                SET group_partner_registration_id = :group_registration
                WHERE id = :id;""")
                .single(call().bind("group_registration", eventGroupPartnerRegistrationId)
                        .bind("id", registrationId))
                .update();
    }

    /**
     * Unties a partner registration from its event group sign-up, so it is a registration of its own.
     *
     * @param registrationId the partner registration
     */
    public void unlink(int registrationId) {
        query("UPDATE event_federation_registration SET group_partner_registration_id = NULL WHERE id = :id;")
                .single(call().bind("id", registrationId))
                .update();
    }

    /**
     * The partner registrations still tied to any partner sign-up of an event group, in one read.
     *
     * @param eventGroupId the event group
     * @return the registrations for parts, by the partner's event group sign-up
     */
    public Map<Integer, List<EventFederationRegistration>> findLinkedByEventGroup(int eventGroupId) {
        return query("""
                SELECT f.id, f.event_id, f.partner_id, f.remote_member_id, f.event_date, f.status, f.created_at,
                       f.agreement_withdrawn_at, f.group_partner_registration_id
                FROM event_federation_registration f
                         JOIN event_group_partner_registration r ON r.id = f.group_partner_registration_id
                WHERE r.event_group_id = :event_group_id;""")
                .single(call().bind("event_group_id", eventGroupId))
                .map(row -> Map.entry(
                        row.getInt("group_partner_registration_id"),
                        EventFederationRegistration.map().map(row)))
                .all()
                .stream()
                .collect(Collectors.groupingBy(
                        Map.Entry::getKey, Collectors.mapping(Map.Entry::getValue, Collectors.toList())));
    }

    /**
     * The partner registrations still tied to a partner's event group sign-up.
     *
     * @param eventGroupPartnerRegistrationId the partner's event group sign-up
     * @return its registrations for parts
     */
    public List<EventFederationRegistration> findLinked(int eventGroupPartnerRegistrationId) {
        return query("""
                SELECT id, event_id, partner_id, remote_member_id, event_date, status, created_at, agreement_withdrawn_at
                FROM event_federation_registration
                WHERE group_partner_registration_id = :group_registration;""")
                .single(call().bind("group_registration", eventGroupPartnerRegistrationId))
                .map(EventFederationRegistration.map())
                .all();
    }

    /**
     * Sets the status of a partner registration, keeping the one it had before.
     *
     * @param registrationId the partner registration
     * @param status         the new status
     */
    public void restate(int registrationId, RegistrationStatus status) {
        query("""
                UPDATE event_federation_registration
                SET previous_status = status, status = :status, status_changed_at = now()
                WHERE id = :id;""")
                .single(call().bind("status", status).bind("id", registrationId))
                .update();
    }
}
