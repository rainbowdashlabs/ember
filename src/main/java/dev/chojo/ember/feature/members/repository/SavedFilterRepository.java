/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.repository;

import dev.chojo.ember.feature.members.entity.FilterTableType;
import dev.chojo.ember.feature.members.entity.SavedFilter;
import dev.chojo.ember.util.sql.SqlSupport;
import jakarta.inject.Singleton;

import java.util.List;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;

/**
 * Repository for persisting and retrieving user-saved table filters.
 */
@Singleton
public class SavedFilterRepository {
    private static final String SAVED_FILTER_COLUMNS = "id, account_id, table_type, name, filter_data, position";

    public List<SavedFilter> findByAccountAndTable(int accountId, FilterTableType tableType) {
        return query("""
                        SELECT %s FROM saved_filter WHERE account_id = :accountId AND table_type = :tableType ORDER BY position;""", SAVED_FILTER_COLUMNS)
                .single(call().bind("accountId", accountId).bind("tableType", tableType))
                .map(SavedFilter.map())
                .all();
    }

    public SavedFilter create(int accountId, FilterTableType tableType, String name, String filterData, int position) {
        return SqlSupport.insertReturning(
                """
                INSERT INTO saved_filter(account_id, table_type, name, filter_data, position)
                VALUES(:accountId, :tableType, :name, :filterData::JSONB, :position)
                RETURNING %s;""".formatted(SAVED_FILTER_COLUMNS),
                call().bind("accountId", accountId)
                        .bind("tableType", tableType)
                        .bind("name", name)
                        .bind("filterData", filterData)
                        .bind("position", position),
                SavedFilter.map());
    }

    public boolean delete(int id, int accountId) {
        return query("DELETE FROM saved_filter WHERE id = :id AND account_id = :accountId;")
                .single(call().bind("id", id).bind("accountId", accountId))
                .delete()
                .changed();
    }
}
