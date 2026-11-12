/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.statistics.repository;

import de.chojo.sadu.queries.api.call.Call;
import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.cluster.entity.StationKind;
import dev.chojo.ember.feature.federation.entity.FederationPartner.FederationStatus;
import dev.chojo.ember.feature.statistics.entity.PeopleByType;
import jakarta.inject.Singleton;

import java.time.LocalDate;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;

/**
 * Writes how the instance is used and how large it is, one row a day in {@code instance_usage_daily}.
 *
 * <p>The day is always the database's {@code CURRENT_DATE}, so the live count and the snapshot fall
 * on the same day as every other daily figure. Only regular stations that are still on this
 * instance count: an association's home station holds nobody, and a station that moved away is no
 * longer this instance's.
 */
@Singleton
public class UsageRepository {

    private static final String REGULAR_STATION = "s.station_kind = :regular AND s.moved_away_at IS NULL";

    private static final String PERSON = "coalesce('a' || sm.account_id, 'm' || sm.id)";

    private static final String PEOPLE_BY_TYPE = """
            SELECT
                count(DISTINCT %1$s)                                       AS people,
                count(DISTINCT %1$s) FILTER (WHERE sm.user_type = :trial)    AS people_trial,
                count(DISTINCT %1$s) FILTER (WHERE sm.user_type = :member)   AS people_member,
                count(DISTINCT %1$s) FILTER (WHERE sm.user_type = :guardian) AS people_guardian,
                count(DISTINCT %1$s) FILTER (WHERE sm.user_type = :team)     AS people_team,
                count(DISTINCT %1$s) FILTER (WHERE sm.user_type = :manager)  AS people_manager,
                count(sm.id)                                                AS memberships
            FROM
                station_member sm
                JOIN station s ON s.id = sm.station_id
            WHERE sm.former = FALSE
              AND %2$s""".formatted(PERSON, REGULAR_STATION);

    /**
     * Notes that an account used the instance today. The first time on a day it moves the account's
     * last active day and adds one to the day's count; every further call that day changes nothing,
     * so an account costs one write a day and the count holds no names. Two requests of the same
     * account at once count once: the second waits for the first's row lock and then finds the day
     * already written.
     *
     * @param accountId the account
     * @return {@code true} if this was the account's first use today
     */
    public boolean markActive(int accountId) {
        return query("""
                        WITH marked AS (
                            UPDATE account
                            SET last_active_on = CURRENT_DATE
                            WHERE id = :id
                              AND last_active_on IS DISTINCT FROM CURRENT_DATE
                            RETURNING id)
                        INSERT INTO instance_usage_daily (day, active_accounts)
                        SELECT CURRENT_DATE, count(*) FROM marked HAVING count(*) > 0
                        ON CONFLICT (day) DO UPDATE
                            SET active_accounts = instance_usage_daily.active_accounts + excluded.active_accounts;""").single(call().bind("id", accountId)).update().changed();
    }

    /**
     * The people of the instance by member type as they stand now, read with the same query the
     * snapshot writes, so the figure on the page and the history agree.
     *
     * @return the counts
     */
    public PeopleByType peopleByType() {
        return query("%s;", PEOPLE_BY_TYPE)
                .single(peopleCall())
                .map(row -> new PeopleByType(
                        row.getInt("people"),
                        row.getInt("people_trial"),
                        row.getInt("people_member"),
                        row.getInt("people_guardian"),
                        row.getInt("people_team"),
                        row.getInt("people_manager"),
                        row.getInt("memberships")))
                .first()
                .orElseThrow();
    }

    /**
     * Writes today's snapshot of how large the instance is, replacing an earlier one of the same day.
     * The live count of people using the instance today is left as it is.
     *
     * <p>Every record counted belongs to a regular station still on this instance, the same stations
     * the people are counted in: attendance reaches its station through its sheet's template,
     * equipment through its inventory, and partnerships an association manages for its stations are
     * left out. The mails sent are read for the day the mail queue counts them under, which is the
     * application's calendar, and yesterday's figure is written again, so the mails of yesterday's
     * last hour are not lost to the snapshot taken before them.
     *
     * @param mailDay today in the calendar the mail queue counts its mails by
     */
    public void snapshot(LocalDate mailDay) {
        query("""
                        WITH
                            regular AS (SELECT s.id FROM station s WHERE %2$s),
                            people AS (%1$s),
                            accounts AS (
                                SELECT
                                    count(*)                                                 AS total,
                                    count(*) FILTER (WHERE email_verified
                                                     AND lower(email) NOT LIKE :synthetic) AS confirmed
                                FROM account),
                            stations AS (
                                SELECT
                                    count(*)                                                AS total,
                                    count(*) FILTER (WHERE s.setup_completed_at IS NOT NULL) AS set_up
                                FROM station s
                                WHERE %2$s)
                        INSERT INTO instance_usage_daily (
                            day, accounts, accounts_confirmed, stations, stations_set_up, members, former_members,
                            events, attendance_sessions, attendance_entries, inventory_items, profile_fields,
                            member_groups, clusters, federation_partners_active, knowledge_base_articles, mails_sent,
                            people, people_trial, people_member, people_guardian, people_team, people_manager,
                            memberships)
                        SELECT
                            CURRENT_DATE,
                            accounts.total,
                            accounts.confirmed,
                            stations.total,
                            stations.set_up,
                            people.memberships,
                            (SELECT count(*)
                             FROM station_member sm JOIN regular r ON r.id = sm.station_id
                             WHERE sm.former = TRUE),
                            (SELECT count(*) FROM station_event e JOIN regular r ON r.id = e.station_id),
                            (SELECT count(*)
                             FROM attendance_session a
                                  JOIN attendance_template t ON t.id = a.template_id
                                  JOIN regular r ON r.id = t.station_id),
                            (SELECT count(*)
                             FROM attendance_entry e
                                  JOIN attendance_session a ON a.id = e.session_id
                                  JOIN attendance_template t ON t.id = a.template_id
                                  JOIN regular r ON r.id = t.station_id),
                            (SELECT count(*)
                             FROM inventory_item ii
                                  JOIN inventory i ON i.id = ii.inventory_id
                                  JOIN regular r ON r.id = i.station_id),
                            (SELECT count(*) FROM profile_field f JOIN regular r ON r.id = f.station_id),
                            (SELECT count(*) FROM member_group g JOIN regular r ON r.id = g.station_id),
                            (SELECT count(*) FROM cluster),
                            (SELECT count(*)
                             FROM federation_partner p JOIN regular r ON r.id = p.station_id
                             WHERE p.status = :partner_active AND NOT p.cluster_managed),
                            (SELECT count(*)
                             FROM kb_file k JOIN regular r ON r.id = k.station_id
                             WHERE k.deleted_at IS NULL),
                            (SELECT coalesce(max(count), 0) FROM email_daily_count WHERE day = :mail_day),
                            people.people,
                            people.people_trial,
                            people.people_member,
                            people.people_guardian,
                            people.people_team,
                            people.people_manager,
                            people.memberships
                        FROM people, accounts, stations
                        ON CONFLICT (day) DO UPDATE SET
                            accounts                   = excluded.accounts,
                            accounts_confirmed         = excluded.accounts_confirmed,
                            stations                   = excluded.stations,
                            stations_set_up            = excluded.stations_set_up,
                            members                    = excluded.members,
                            former_members             = excluded.former_members,
                            events                     = excluded.events,
                            attendance_sessions        = excluded.attendance_sessions,
                            attendance_entries         = excluded.attendance_entries,
                            inventory_items            = excluded.inventory_items,
                            profile_fields             = excluded.profile_fields,
                            member_groups              = excluded.member_groups,
                            clusters                   = excluded.clusters,
                            federation_partners_active = excluded.federation_partners_active,
                            knowledge_base_articles    = excluded.knowledge_base_articles,
                            mails_sent                 = excluded.mails_sent,
                            people                     = excluded.people,
                            people_trial               = excluded.people_trial,
                            people_member              = excluded.people_member,
                            people_guardian            = excluded.people_guardian,
                            people_team                = excluded.people_team,
                            people_manager             = excluded.people_manager,
                            memberships                = excluded.memberships;""", PEOPLE_BY_TYPE, REGULAR_STATION)
                .single(peopleCall()
                        .bind("synthetic", "%" + Account.SYNTHETIC_EMAIL_SUFFIX)
                        .bind("partner_active", FederationStatus.ACTIVE)
                        .bind("mail_day", mailDay))
                .update();
        query("""
                        UPDATE instance_usage_daily
                        SET mails_sent = coalesce((SELECT count FROM email_daily_count WHERE day = :mail_day), 0)
                        WHERE day = :mail_day;""").single(call().bind("mail_day", mailDay.minusDays(1))).update();
    }

    private static Call peopleCall() {
        return call().bind("trial", StationUserType.TRIAL)
                .bind("member", StationUserType.MEMBER)
                .bind("guardian", StationUserType.GUARDIAN)
                .bind("team", StationUserType.TEAM)
                .bind("manager", StationUserType.MANAGER)
                .bind("regular", StationKind.REGULAR);
    }
}
