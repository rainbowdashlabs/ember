/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.account.repository;

import dev.chojo.ember.feature.account.entity.AccountOverview;
import dev.chojo.ember.util.sql.MemberNameSql;
import dev.chojo.ember.util.sql.WhereBuilder;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.util.List;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;

/**
 * The instance's accounts, read the way its account list shows them: one row per account with its
 * stations, its association roles and the state of everything it signs in with.
 */
@Singleton
public class AccountOverviewRepository {

    private static final String SEARCH = """
            WHERE LOWER(%s) LIKE :q
               OR LOWER(a.email) LIKE :q
               OR LOWER(COALESCE(a.username, '')) LIKE :q""".formatted(MemberNameSql.ofAccount("a"));

    /**
     * One page of accounts, by name.
     *
     * @param search a part of the name, the address or the sign-in name, or {@code null} for every account
     * @param limit  how many to answer at most
     * @param offset how many to pass over first
     * @return the page
     */
    public List<AccountOverview> page(@Nullable String search, int limit, int offset) {
        var where = WhereBuilder.create().like(SEARCH, "q", search);
        return query("""
                SELECT
                    a.id,
                    a.uid,
                    %s AS name,
                    a.email,
                    a.username,
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
                    (SELECT COUNT(*)
                     FROM account_2fa_factor f
                     JOIN account_2fa_webauthn w ON w.factor_id = f.id
                     WHERE f.account_id = a.id AND f.disabled_at IS NULL AND w.sign_in)::INT AS passkeys,
                    EXISTS(SELECT 1
                           FROM account_2fa_factor f
                           LEFT JOIN account_2fa_webauthn w ON w.factor_id = f.id
                           WHERE f.account_id = a.id
                             AND f.disabled_at IS NULL
                             AND f.kind != CAST('BACKUP_CODES' AS two_factor_kind)
                             AND (f.kind != CAST('WEBAUTHN' AS two_factor_kind) OR w.second_factor)) AS two_factor
                FROM account a
                LEFT JOIN account_credential cred ON cred.account_id = a.id
                %s
                ORDER BY LOWER(%s), a.id
                LIMIT :limit OFFSET :offset;""", MemberNameSql.ofAccount("a"), where.fragment(), MemberNameSql.ofAccount("a"))
                .single(where.apply(call().bind("limit", limit).bind("offset", offset)))
                .map(AccountOverview.map())
                .all();
    }

    /**
     * How many accounts a search finds in all.
     *
     * @param search the same search as {@link #page}
     * @return the count
     */
    public int count(@Nullable String search) {
        var where = WhereBuilder.create().like(SEARCH, "q", search);
        return query("""
                SELECT COUNT(*)::INT AS total
                FROM account a
                %s;""", where.fragment())
                .single(where.apply(call()))
                .map(row -> row.getInt("total"))
                .first()
                .orElse(0);
    }
}
