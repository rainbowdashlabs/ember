/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.repository;

import de.chojo.sadu.postgresql.types.PostgreSqlTypes;
import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.feature.members.entity.RuleNode;
import jakarta.inject.Singleton;

import java.time.LocalDate;
import java.util.List;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;

/** The rules dynamic groups and tags take their members from, and who those rules match. */
@Singleton
public class MemberRuleRepository {

    /**
     * The current members of a station a rule matches, in id order.
     *
     * @param stationId  the station whose members are asked about
     * @param root       the root block of the rule
     * @param today      the current day, which a check counts from unless it names a fixed one
     * @param boundTypes the user types the group is bound to, empty for a tag or an unbound group
     */
    public List<Integer> matching(int stationId, RuleNode root, LocalDate today, List<StationUserType> boundTypes) {
        var compiled = MemberRuleSql.compile(root, today);
        List<String> types = boundTypes.stream().map(Enum::name).toList();
        return query("""
                SELECT sm.id
                FROM station_member sm
                WHERE sm.station_id = :station_id
                  AND NOT sm.former
                  AND (cardinality(:bound_types) = 0 OR sm.user_type = ANY(:bound_types))
                  AND %s
                ORDER BY sm.id;""", compiled.predicate())
                .single(compiled.binds()
                        .apply(call().bind("station_id", stationId).bind("bound_types", types, PostgreSqlTypes.TEXT)))
                .map(row -> row.getInt("id"))
                .all();
    }
}
