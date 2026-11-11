/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.station.repository;

import de.chojo.sadu.queries.api.call.Call;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.feature.cluster.entity.StationKind;
import dev.chojo.ember.feature.station.entity.StationOverview;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Optional;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;

/**
 * Reads the stations of the instance the way its administration sees them, every station in one query.
 *
 * <p>A station's manager is read by the rule {@code StationService.findManagerInfo} follows: among the
 * members holding the administrator permission and an account, the owner first, anybody else only while
 * the owner is not one of them. The state follows from that manager and the station's setup: a finished
 * setup is set up whoever runs it, a station without such a manager has none, a manager whose address is
 * not confirmed, or who neither chose a password nor finished their account's setup another way (such as
 * with a passkey), is still waited for, and a manager who can sign in is setting it up. The waiting period
 * and the last reminder are those of the current wait: a station that is set up waits for nobody, and a
 * reminder from before a new manager was named belongs to a wait that is over. Association home stations
 * are never part of it.
 */
@Singleton
public class StationOverviewRepository {

    private static final String OVERVIEW = """
            WITH manager AS (
                SELECT DISTINCT ON (sm.station_id)
                       sm.station_id,
                       a.id AS account_id,
                       a.first_name,
                       a.last_name,
                       a.email,
                       a.email_verified
                           AND (a.setup_completed_at IS NOT NULL
                                OR (c.account_id IS NOT NULL AND NOT c.force_password_change)) AS account_ready
                FROM station_member sm
                JOIN station st ON st.id = sm.station_id
                JOIN station_member_permission smp ON smp.member_id = sm.id
                JOIN station_permission sp ON sp.id = smp.permission_id AND sp.name = :administrator
                JOIN account a ON a.id = sm.account_id
                LEFT JOIN account_credential c ON c.account_id = a.id
                WHERE sm.former = FALSE
                ORDER BY sm.station_id, coalesce(sm.id = st.owner_member_id, FALSE) DESC, sm.id
            ),
            member_count AS (
                SELECT station_id, count(*) AS members
                FROM station_member
                WHERE former = FALSE
                GROUP BY station_id
            ),
            overview AS (
                SELECT s.id,
                       s.uid,
                       s.name,
                       s.locale,
                       CASE
                           WHEN s.setup_completed_at IS NOT NULL THEN 'SET_UP'
                           WHEN m.account_id IS NULL THEN 'NO_MANAGER'
                           WHEN NOT m.account_ready THEN 'WAITING_FOR_MANAGER'
                           ELSE 'SETTING_UP'
                       END AS state,
                       coalesce(mc.members, 0) AS member_count,
                       m.account_id AS manager_account_id,
                       m.first_name AS manager_first_name,
                       m.last_name AS manager_last_name,
                       m.email AS manager_email,
                       CASE WHEN s.setup_completed_at IS NULL THEN s.waiting_since END AS waiting_since,
                       s.created_at,
                       cl.name AS association_name,
                       lr.sent_at AS last_reminder_at,
                       lr.trigger AS last_reminder_trigger,
                       s.moved_away_at IS NOT NULL AS moved_away
                FROM station s
                LEFT JOIN manager m ON m.station_id = s.id
                LEFT JOIN member_count mc ON mc.station_id = s.id
                LEFT JOIN LATERAL (
                    SELECT r.sent_at, r.trigger
                    FROM station_manager_reminder r
                    WHERE r.station_id = s.id
                      AND r.waiting_since = s.waiting_since
                      AND r.sent_at IS NOT NULL
                    ORDER BY r.sent_at DESC, r.id DESC
                    LIMIT 1
                ) lr ON TRUE
                LEFT JOIN cluster cl ON cl.id = s.cluster_id
                WHERE s.station_kind = :regular
            )
            SELECT *
            FROM overview o
            WHERE %s
            ORDER BY o.id;""";

    /**
     * Every station somebody runs, in the order they were made.
     *
     * @return the stations with their state, manager, size and last reminder
     */
    public List<StationOverview> list() {
        return query(OVERVIEW, "TRUE").single(base()).map(StationOverview.map()).all();
    }

    /**
     * One station as the administration sees it.
     *
     * @param stationId the station
     * @return the station, or empty when there is none by that id or it is an association's home
     */
    public Optional<StationOverview> find(int stationId) {
        return query(OVERVIEW, "o.id = :id")
                .single(base().bind("id", stationId))
                .map(StationOverview.map())
                .first();
    }

    private static Call base() {
        return call().bind("administrator", StationPermission.STATION_ADMINISTRATOR)
                .bind("regular", StationKind.REGULAR);
    }
}
