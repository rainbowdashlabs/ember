/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.tracking;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.jspecify.annotations.Nullable;

/**
 * Overrides the default outgoing-FK scope derivation for tables that are reached
 * indirectly through another table (incoming FK).
 *
 * <p>Reads as: "rows on this table belong to a station iff their {@code refColumn} value
 * matches some {@code viaColumn} value on {@code viaTable}, scoped to the target station
 * via {@code viaTable}'s own scope path."
 *
 * <p>Example for {@code account}: {@code viaTable=station_member, viaColumn=account_id,
 * refColumn=id, distinct=true} expresses "an account is in scope when at least one
 * station_member row of the target station references it via {@code account_id}".
 *
 * <p>A row that names two stations, such as a lending request naming the station asking and the
 * station lending, belongs to both: {@code orRefColumn} names the second column, and the row is in
 * scope when either of them matches.
 *
 * @param viaTable    the intermediate table that supplies the station scope
 * @param viaColumn   the column on {@code viaTable} whose values are the relevant ids
 * @param refColumn   the column on this table that's compared to {@code viaColumn} values
 * @param distinct    when {@code true}, the resulting set is deduplicated; needed for accounts
 *                    that may be referenced by multiple members
 * @param orRefColumn a second column on this table compared the same way, or {@code null}
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CustomScope(
        String viaTable,
        String viaColumn,
        String refColumn,
        boolean distinct,
        @Nullable String orRefColumn) {}
