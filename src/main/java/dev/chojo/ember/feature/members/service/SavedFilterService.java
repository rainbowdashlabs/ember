/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.service;

import dev.chojo.ember.api.refusal.MemberRefusal;
import dev.chojo.ember.feature.members.entity.FilterTableType;
import dev.chojo.ember.feature.members.entity.SavedFilter;
import dev.chojo.ember.feature.members.repository.SavedFilterRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.List;

/**
 * The filters a person saved for a table, which are theirs alone.
 */
@Singleton
public class SavedFilterService {
    private final SavedFilterRepository repository;

    @Inject
    public SavedFilterService(SavedFilterRepository repository) {
        this.repository = repository;
    }

    public List<SavedFilter> list(int accountId, FilterTableType tableType) {
        return repository.findByAccountAndTable(accountId, tableType);
    }

    /**
     * Saves a filter at the end of the person's list for that table.
     *
     * @param accountId whose filter it is
     * @param request   the filter
     * @return the saved filter
     */
    public SavedFilter create(int accountId, CreateFilterRequest request) {
        if (request.tableType() == null || request.name() == null || request.filterData() == null) {
            throw MemberRefusal.SAVED_FILTER_DETAILS_MISSING.raise();
        }
        int position =
                repository.findByAccountAndTable(accountId, request.tableType()).size();
        return repository.create(accountId, request.tableType(), request.name(), request.filterData(), position);
    }

    /**
     * Deletes one of the person's filters. A filter of somebody else is not there for them.
     *
     * @param accountId whose filter it is
     * @param id        the filter
     */
    public void delete(int accountId, int id) {
        if (!repository.delete(id, accountId)) {
            throw MemberRefusal.SAVED_FILTER_NOT_HERE_ON_DELETE.raise();
        }
    }

    public record CreateFilterRequest(FilterTableType tableType, String name, String filterData) {}
}
