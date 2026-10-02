/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.account.service;

import dev.chojo.ember.feature.account.repository.AccountOverviewRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The account list's paging: a page or size out of range is brought back into it.
 */
class AccountOverviewServiceTest {

    @Test
    void aPageOutOfRangeIsBroughtBackIntoIt() {
        var repository = mock(AccountOverviewRepository.class);
        when(repository.page("x", AccountOverviewService.MAX_SIZE, 0)).thenReturn(List.of());
        when(repository.count("x")).thenReturn(7);
        var service = new AccountOverviewService(repository);

        var page = service.list("x", -4, 5000);

        assertEquals(0, page.page());
        assertEquals(AccountOverviewService.MAX_SIZE, page.size());
        assertEquals(7, page.total());
        service.list("x", 3, 0);
        verify(repository).page("x", 1, 3);
    }
}
