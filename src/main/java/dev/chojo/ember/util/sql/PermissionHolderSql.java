/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.util.sql;

import de.chojo.sadu.postgresql.types.PostgreSqlTypes;
import de.chojo.sadu.queries.api.call.Call;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.auth.StationUserType;

import java.util.Arrays;
import java.util.Collection;

/**
 * The one rule a statement uses to ask who holds a station permission, so the member lists and the
 * notifications cannot drift apart.
 *
 * <p>It is the rule the member permission resolver applies to one member, written for a whole
 * station at once: what their user type carries everywhere, what the station grants their user
 * type, what was granted to them directly or through a group, and the guardian right that follows
 * from looking after somebody who has not left. The two are held to each other by test.
 *
 * <p>Which user types and grant names count is worked out from the permission hierarchy in
 * {@link #bind(Call, Collection)}, so the statement only matches stored rows against lists the
 * enums produced.
 */
public final class PermissionHolderSql {
    /**
     * Whether the station member aliased {@code sm} holds one of the bound permissions. Bind its
     * parameters with {@link #bind(Call, Collection)}.
     */
    public static final String HOLDS_PERMISSION = """
            sm.user_type = ANY(:default_user_types)
            OR exists (
                SELECT 1 FROM station_user_type_permission sutp
                JOIN station_permission sp ON sp.id = sutp.permission_id
                WHERE sutp.station_id = sm.station_id
                  AND sutp.user_type = sm.user_type
                  AND sp.name = ANY(:permission_names)
            )
            OR exists (
                SELECT 1 FROM station_member_permission smp
                JOIN station_permission sp ON sp.id = smp.permission_id
                WHERE smp.member_id = sm.id AND sp.name = ANY(:permission_names)
            )
            OR exists (
                SELECT 1 FROM member_group_entry mge
                JOIN member_group_permission mgp ON mgp.group_id = mge.group_id
                JOIN station_permission sp ON sp.id = mgp.permission_id
                WHERE mge.member_id = sm.id AND sp.name = ANY(:permission_names)
            )
            OR (:guardian_grants AND exists (
                SELECT 1 FROM member_manager mm
                JOIN station_member managed ON managed.id = mm.managed_id
                WHERE mm.manager_id = sm.id AND managed.former = FALSE
            ))""";

    private PermissionHolderSql() {}

    /**
     * Binds what {@link #HOLDS_PERMISSION} reads for holders of any of the given permissions.
     *
     * <p>The rule is a disjunction of lookups against lists, so the holders of several permissions
     * are found by joining the lists. An empty set matches nobody.
     *
     * @param call        the call to bind onto
     * @param permissions the permissions of which a member must hold at least one
     * @return the same call
     */
    public static Call bind(Call call, Collection<StationPermission> permissions) {
        var defaultTypes = Arrays.stream(StationUserType.values())
                .filter(type -> permissions.stream().anyMatch(type::grantsByDefault))
                .map(StationUserType::name)
                .toList();
        var names = permissions.stream()
                .flatMap(permission -> permission.grantedBy().stream())
                .distinct()
                .toList();
        return call.bind("default_user_types", defaultTypes, PostgreSqlTypes.VARCHAR)
                .bind("permission_names", names, PostgreSqlTypes.VARCHAR)
                .bind("guardian_grants", names.contains(StationPermission.MEMBER_GUARDIAN.name()));
    }
}
