/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.repository;

import de.chojo.sadu.queries.api.call.Call;
import de.chojo.sadu.queries.api.query.Query;
import dev.chojo.ember.feature.members.entity.SavedFilter;
import jakarta.inject.Singleton;

import java.util.List;

/** Repository for persisting and retrieving user-saved table filters. */
@Singleton
public class SavedFilterRepository {

    /**
     * Finds all saved filters for a given account and table type, ordered by position.
     *
     * @param accountId the account identifier
     * @param tableType the table type to filter by
     * @return the list of matching saved filters
     */
    public List<SavedFilter> findByAccountAndTable(int accountId, String tableType) {
        return Query.query(
                        "SELECT id, account_id, table_type, name, filter_data, position FROM saved_filter WHERE account_id = :accountId AND table_type = :tableType ORDER BY position;")
                .single(Call.of().bind("accountId", accountId).bind("tableType", tableType))
                .map(SavedFilter.map())
                .all();
    }

    /**
     * Creates a new saved filter.
     *
     * @param accountId  the owning account identifier
     * @param tableType  the table type this filter applies to
     * @param name       the user-defined filter name
     * @param filterData the filter configuration as JSON
     * @param position   the display order position
     * @return the created saved filter
     */
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

    /**
     * Deletes a saved filter owned by the given account.
     *
     * @param id        the filter identifier
     * @param accountId the owning account identifier
     * @return true if a filter was deleted
     */
    public boolean delete(int id, int accountId) {
        return Query.query("DELETE FROM saved_filter WHERE id = :id AND account_id = :accountId;")
                .single(Call.of().bind("id", id).bind("accountId", accountId))
                .delete()
                .changed();
    }
}
