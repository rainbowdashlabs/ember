/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.account.repository;

import dev.chojo.ember.feature.account.entity.AccountListQuery;
import dev.chojo.ember.feature.account.entity.AccountOverview;
import dev.chojo.ember.util.sql.MemberNameSql;
import dev.chojo.ember.util.sql.WhereBuilder;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.time.ZoneId;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

/**
 * The instance's accounts, read the way its account list shows them: one row per account with its
 * stations, its association roles and the state of everything it signs in with.
 *
 * <p>The page and the count are narrowed by one clause built from the same query, so the number of
 * accounts the list reports is always the number its pages hold. The password state, the passkey
 * count and the second-factor rule are each one fragment that both the selected columns and the
 * filters read.
 *
 * <p>A day of the last sign-in is a calendar day in the instance's zone, turned into the moments it
 * starts and ends before the statement sees it, so the database's own session zone plays no part.
 * The instance keeps no zone of its own, so that zone is the one the server runs in.
 */
@Singleton
public class AccountOverviewRepository {

    private static final String NAME = MemberNameSql.ofAccount("a");

    private static final String FROM = """
            FROM account a
            LEFT JOIN account_credential cred ON cred.account_id = a.id""";

    private static final String SEARCH = """
            AND (LOWER(%s) LIKE :q
                 OR LOWER(a.email) LIKE :q
                 OR LOWER(COALESCE(a.username, '')) LIKE :q)""".formatted(NAME);

    private static final String PASSWORD_STATE = """
            CASE
                WHEN cred.account_id IS NULL THEN 'NONE'
                WHEN cred.password_login_disabled_at IS NOT NULL THEN 'OFF'
                WHEN cred.one_time_password_expires_at IS NOT NULL THEN 'ONE_TIME'
                ELSE 'ON'
            END""";

    private static final String PASSKEYS = """
            (SELECT COUNT(*)
             FROM account_2fa_factor f
             JOIN account_2fa_webauthn w ON w.factor_id = f.id
             WHERE f.account_id = a.id AND f.disabled_at IS NULL AND w.sign_in)::INT""";

    private static final String TWO_FACTOR = """
            EXISTS(SELECT 1
                   FROM account_2fa_factor f
                   LEFT JOIN account_2fa_webauthn w ON w.factor_id = f.id
                   WHERE f.account_id = a.id
                     AND f.disabled_at IS NULL
                     AND f.kind != CAST('BACKUP_CODES' AS two_factor_kind)
                     AND (f.kind != CAST('WEBAUTHN' AS two_factor_kind) OR w.second_factor))""";

    private static final String REAL_ADDRESS =
            "a.email IS NOT NULL AND a.email <> '' AND LOWER(a.email) NOT LIKE '%.local'";

    private static final String AWAITS_SETUP =
            "a.setup_completed_at IS NULL AND (cred.account_id IS NULL OR cred.force_password_change)";

    private static final String IN_STATION = """
            EXISTS(SELECT 1
                   FROM station_member sm
                   JOIN station s ON s.id = sm.station_id
                   WHERE sm.account_id = a.id AND NOT sm.former AND s.uid::TEXT = ANY(:stations))""";

    private static final String IN_NO_STATION = """
            NOT EXISTS(SELECT 1 FROM station_member sm WHERE sm.account_id = a.id AND NOT sm.former)""";

    private static final String IN_ASSOCIATION = """
            EXISTS(SELECT 1
                   FROM cluster_member cm
                   JOIN cluster c ON c.id = cm.cluster_id
                   WHERE cm.account_id = a.id AND c.uid::TEXT = ANY(:associations))""";

    private static final String IN_NO_ASSOCIATION = """
            NOT EXISTS(SELECT 1 FROM cluster_member cm WHERE cm.account_id = a.id)""";

    private static final String SIGNED_IN_FROM = "a.last_sign_in_at >= :signed_in_from";

    private static final String SIGNED_IN_TO = "a.last_sign_in_at < :signed_in_to";

    private final ZoneId zone;

    /** Reads days of the last sign-in in the zone the server runs in. */
    @Inject
    public AccountOverviewRepository() {
        this(ZoneId.systemDefault());
    }

    /**
     * Reads days of the last sign-in in the given zone.
     *
     * @param zone the zone whose calendar days the filters name
     */
    public AccountOverviewRepository(ZoneId zone) {
        this.zone = zone;
    }

    /**
     * One page of accounts, in the order the query asks for.
     *
     * @param query  what the list is narrowed to and how it is ordered
     * @param limit  how many to answer at most
     * @param offset how many to pass over first
     * @return the page
     */
    public List<AccountOverview> page(AccountListQuery query, int limit, int offset) {
        var where = where(query);
        return query("""
                SELECT
                    a.id,
                    a.uid,
                    %s AS name,
                    a.email,
                    a.username,
                    a.email_verified,
                    a.instance_user_type,
                    a.last_sign_in_at,
                    ARRAY(SELECT s.name
                          FROM station_member sm
                          JOIN station s ON s.id = sm.station_id
                          WHERE sm.account_id = a.id AND NOT sm.former
                          ORDER BY s.name) AS stations,
                    ARRAY(SELECT c.name
                          FROM cluster_member cm
                          JOIN cluster c ON c.id = cm.cluster_id
                          WHERE cm.account_id = a.id
                          ORDER BY c.name, cm.id) AS association_names,
                    ARRAY(SELECT cm.user_type
                          FROM cluster_member cm
                          JOIN cluster c ON c.id = cm.cluster_id
                          WHERE cm.account_id = a.id
                          ORDER BY c.name, cm.id) AS association_roles,
                    cred.account_id IS NOT NULL AS has_password,
                    cred.account_id IS NOT NULL AND cred.password_login_disabled_at IS NULL AS password_sign_in,
                    cred.one_time_password_expires_at,
                    %s AS awaits_setup,
                    %s AS passkeys,
                    %s AS two_factor
                %s
                WHERE TRUE
                %s
                ORDER BY %s
                LIMIT :limit OFFSET :offset;""", NAME, AWAITS_SETUP, PASSKEYS, TWO_FACTOR, FROM, where.fragment(), orderOf(query))
                .single(where.apply(call().bind("limit", limit).bind("offset", offset)))
                .map(AccountOverview.map())
                .all();
    }

    /**
     * How many accounts the query finds in all.
     *
     * @param query the same query as {@link #page}
     * @return the count
     */
    public int count(AccountListQuery query) {
        var where = where(query);
        return query("""
                SELECT COUNT(*)::INT AS total
                %s
                WHERE TRUE
                %s;""", FROM, where.fragment())
                .single(where.apply(call()))
                .map(row -> row.getInt("total"))
                .first()
                .orElse(0);
    }

    private WhereBuilder where(AccountListQuery query) {
        var where = WhereBuilder.create()
                .like(SEARCH, "q", query.search())
                .anyOf(
                        "AND a.instance_user_type = ANY(:roles)",
                        "roles",
                        query.roles().stream().map(Enum::name).toList())
                .anyOf(
                        "AND (%s) = ANY(:passwords)".formatted(PASSWORD_STATE),
                        "passwords",
                        query.passwords().stream().map(Enum::name).toList())
                .add("AND %s = :two_factor".formatted(TWO_FACTOR), "two_factor", query.twoFactor())
                .add("AND %s >= :passkeys_min".formatted(PASSKEYS), "passkeys_min", query.passkeysMin())
                .add("AND %s <= :passkeys_max".formatted(PASSKEYS), "passkeys_max", query.passkeysMax());
        address(where, query);
        signIn(where, query);
        heldOrNone(where, IN_STATION, IN_NO_STATION, "stations", query.stations(), query.withoutStation());
        heldOrNone(
                where,
                IN_ASSOCIATION,
                IN_NO_ASSOCIATION,
                "associations",
                query.associations(),
                query.withoutAssociation());
        return where;
    }

    /**
     * A real address confirmed or not, or only a stand-in address where that is asked too: the two
     * are alternatives, as an answer and an empty cell are in a yes or no filter.
     */
    private static void address(WhereBuilder where, AccountListQuery query) {
        Boolean confirmed = query.confirmed();
        String standIn = "NOT (%s)".formatted(REAL_ADDRESS);
        if (confirmed == null) {
            where.addIf(query.withoutAddress(), "AND %s".formatted(standIn));
            return;
        }
        String held =
                (confirmed ? "(%s AND a.email_verified)" : "(%s AND NOT a.email_verified)").formatted(REAL_ADDRESS);
        where.add(query.withoutAddress() ? "AND (%s OR %s)".formatted(held, standIn) : "AND %s".formatted(held));
    }

    /**
     * The last sign-in between two days, or none at all where that is asked too: the two are
     * alternatives, as an empty cell and a range are in a date filter.
     */
    private void signIn(WhereBuilder where, AccountListQuery query) {
        var from = query.signedInFrom();
        var to = query.signedInTo();
        if (from == null && to == null) {
            where.addIf(query.neverSignedIn(), "AND a.last_sign_in_at IS NULL");
            return;
        }
        String range;
        if (from != null && to != null) {
            range = "%s AND %s".formatted(SIGNED_IN_FROM, SIGNED_IN_TO);
        } else {
            range = from != null ? SIGNED_IN_FROM : SIGNED_IN_TO;
        }
        String fragment = query.neverSignedIn()
                ? "AND (a.last_sign_in_at IS NULL OR (%s))".formatted(range)
                : "AND %s".formatted(range);
        where.add(fragment, call -> {
            var bound = call;
            if (from != null) {
                bound = bound.bind("signed_in_from", from.atStartOfDay(zone).toInstant(), INSTANT_TIMESTAMP);
            }
            if (to != null) {
                bound = bound.bind(
                        "signed_in_to", to.plusDays(1).atStartOfDay(zone).toInstant(), INSTANT_TIMESTAMP);
            }
            return bound;
        });
    }

    /**
     * Membership in one of the given places, or in none at all where that is asked too.
     */
    private static void heldOrNone(
            WhereBuilder where, String held, String none, String parameter, Collection<UUID> uids, boolean orNone) {
        var values = uids.stream().map(UUID::toString).toList();
        if (values.isEmpty()) {
            where.addIf(orNone, "AND %s".formatted(none));
            return;
        }
        String fragment = orNone ? "AND (%s OR %s)".formatted(held, none) : "AND %s".formatted(held);
        where.anyOf(fragment, parameter, values);
    }

    private static String orderOf(AccountListQuery query) {
        String direction = query.descending() ? "DESC" : "ASC";
        String byName = "LOWER(%s) %s, a.id".formatted(NAME, direction);
        return switch (query.sort()) {
            case NAME -> byName;
            case LAST_SIGN_IN -> "a.last_sign_in_at %s NULLS LAST, LOWER(%s), a.id".formatted(direction, NAME);
            case PASSKEYS -> "%s %s, LOWER(%s), a.id".formatted(PASSKEYS, direction, NAME);
        };
    }
}
