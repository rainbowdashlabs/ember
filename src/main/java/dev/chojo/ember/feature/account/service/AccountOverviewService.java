/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.account.service;

import dev.chojo.ember.feature.account.entity.AccountListQuery;
import dev.chojo.ember.feature.account.entity.AccountOverview;
import dev.chojo.ember.feature.account.repository.AccountOverviewRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.List;

/**
 * The instance's account list, a page at a time. The paging, the filters and the order happen in the
 * database, because an instance holds every member of every station and the list must not grow with
 * it, and a filter that only saw one page would say nothing about the others.
 */
@Singleton
public class AccountOverviewService {

    /** How many accounts a page holds when the caller does not say. */
    public static final int DEFAULT_SIZE = 25;

    /** How many accounts a page may hold at most. */
    public static final int MAX_SIZE = 100;

    private final AccountOverviewRepository repository;

    @Inject
    public AccountOverviewService(AccountOverviewRepository repository) {
        this.repository = repository;
    }

    /**
     * One page of the list. A page or size out of range is brought back into it rather than refused.
     *
     * @param query what the list is narrowed to and how it is ordered
     * @param page  the page, counted from zero
     * @param size  how many accounts a page holds
     * @return the page with the number of accounts the query finds in all
     */
    public AccountOverviewPage list(AccountListQuery query, int page, int size) {
        int pageSize = Math.clamp(size, 1, MAX_SIZE);
        int pageIndex = Math.max(page, 0);
        return new AccountOverviewPage(
                repository.page(query, pageSize, pageIndex * pageSize), repository.count(query), pageIndex, pageSize);
    }

    /**
     * One page of the instance's account list.
     *
     * @param accounts the accounts on it
     * @param total    how many accounts the query finds in all
     * @param page     which page it is, counted from zero
     * @param size     how many accounts a page holds
     */
    public record AccountOverviewPage(List<AccountOverview> accounts, int total, int page, int size) {}
}
