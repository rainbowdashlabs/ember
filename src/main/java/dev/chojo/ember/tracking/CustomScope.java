/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.tracking;

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
 * @param viaTable  the intermediate table that supplies the station scope
 * @param viaColumn the column on {@code viaTable} whose values are the relevant ids
 * @param refColumn the column on this table that's compared to {@code viaColumn} values
 * @param distinct  when {@code true}, the resulting set is deduplicated; needed for accounts
 *                  that may be referenced by multiple members
 */
public record CustomScope(String viaTable, String viaColumn, String refColumn, boolean distinct) {}
