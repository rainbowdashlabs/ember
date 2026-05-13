/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.repository;

import de.chojo.sadu.queries.api.call.Call;
import de.chojo.sadu.queries.api.query.Query;
import dev.chojo.ember.entity.SavedFilter;
import jakarta.inject.Singleton;

import java.util.List;

@Singleton
public class SavedFilterRepository {

    public List<SavedFilter> findByAccountAndTable(int accountId, String tableType) {
        return Query.query(
                        "SELECT id, account_id, table_type, name, filter_data, position FROM saved_filter WHERE account_id = :accountId AND table_type = :tableType ORDER BY position;")
                .single(Call.of().bind("accountId", accountId).bind("tableType", tableType))
                .map(SavedFilter.map())
                .all();
    }

    public SavedFilter create(int accountId, String tableType, String name, String filterData, int position) {
        return Query.query(
                        "INSERT INTO saved_filter(account_id, table_type, name, filter_data, position) VALUES(:accountId, :tableType, :name, :filterData::JSONB, :position) RETURNING id, account_id, table_type, name, filter_data, position;")
                .single(Call.of()
                        .bind("accountId", accountId)
                        .bind("tableType", tableType)
                        .bind("name", name)
                        .bind("filterData", filterData)
                        .bind("position", position))
                .map(SavedFilter.map())
                .first()
                .orElseThrow();
    }

    public boolean delete(int id, int accountId) {
        return Query.query("DELETE FROM saved_filter WHERE id = :id AND account_id = :accountId;")
                .single(Call.of().bind("id", id).bind("accountId", accountId))
                .delete()
                .changed();
    }
}
