/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.feed.repository;

import de.chojo.sadu.postgresql.types.PostgreSqlTypes;
import dev.chojo.ember.util.sql.Transactions;
import jakarta.inject.Singleton;

import java.util.List;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;

/**
 * Repository for the {@code user_feed_hidden_category} table: the categories a member's personal
 * calendar feed leaves out.
 */
@Singleton
public class PersonalFeedFilterRepository {

    /**
     * The categories a member's personal feed leaves out.
     *
     * @param memberId the member
     * @return the category ids, in id order
     */
    public List<Integer> findHiddenCategories(int memberId) {
        return query("""
                        SELECT category_id FROM user_feed_hidden_category
                        WHERE member_id = :member_id
                        ORDER BY category_id;""")
                .single(call().bind("member_id", memberId))
                .map(row -> row.getInt("category_id"))
                .all();
    }

    /**
     * Replaces the categories a member's personal feed leaves out. Ids that are not categories of the
     * member's station are skipped; the caller checks them before.
     *
     * @param memberId    the member
     * @param categoryIds the categories the feed leaves out from now on
     */
    public void replaceHiddenCategories(int memberId, List<Integer> categoryIds) {
        Transactions.run(() -> {
            query("DELETE FROM user_feed_hidden_category WHERE member_id = :member_id;")
                    .single(call().bind("member_id", memberId))
                    .delete();
            if (categoryIds.isEmpty()) return;
            query("""
                            INSERT INTO user_feed_hidden_category(member_id, category_id)
                            SELECT sm.id, c.id
                            FROM station_member sm
                            JOIN event_category c ON c.station_id = sm.station_id
                            WHERE sm.id = :member_id AND c.id = ANY(:category_ids::INT[])
                            ON CONFLICT DO NOTHING;""")
                    .single(call().bind("member_id", memberId)
                            .bind("category_ids", categoryIds, PostgreSqlTypes.INTEGER))
                    .insert();
        });
    }
}
