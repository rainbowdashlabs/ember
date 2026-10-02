/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.account.service;

import dev.chojo.ember.feature.account.entity.AccountOverview;
import dev.chojo.ember.feature.account.repository.AccountOverviewRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * The instance's account list, a page at a time. The paging happens in the database, because an
 * instance holds every member of every station and the list must not grow with it.
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
     * @param search a part of the name, the address or the sign-in name, or {@code null} for every account
     * @param page   the page, counted from zero
     * @param size   how many accounts a page holds
     * @return the page with the number of accounts the search finds in all
     */
    public AccountOverviewPage list(@Nullable String search, int page, int size) {
        int pageSize = Math.clamp(size, 1, MAX_SIZE);
        int pageIndex = Math.max(page, 0);
        return new AccountOverviewPage(
                repository.page(search, pageSize, pageIndex * pageSize), repository.count(search), pageIndex, pageSize);
    }

    /**
     * One page of the instance's account list.
     *
     * @param accounts the accounts on it
     * @param total    how many accounts the search finds in all
     * @param page     which page it is, counted from zero
     * @param size     how many accounts a page holds
     */
    public record AccountOverviewPage(List<AccountOverview> accounts, int total, int page, int size) {}
}
