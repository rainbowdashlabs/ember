/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.repository;

import dev.chojo.ember.entity.Account;
import dev.chojo.ember.entity.SavedFilter;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SavedFilterRepositoryTest extends RepositoryTestBase {
    private static Account account;
    private static int filterId;

    @BeforeAll
    static void setup() {
        account = accountRepo.create("filter@test.com", "Filter", "User");
    }

    @AfterAll
    static void cleanup() {
        accountRepo.delete(account.id());
    }

    @Test
    @Order(1)
    void create() {
        SavedFilter filter =
                savedFilterRepo.create(account.id(), "members", "Active Members", "{\"status\":\"active\"}", 1);
        assertNotNull(filter);
        assertEquals("members", filter.tableType());
        assertEquals("Active Members", filter.name());
        assertEquals(1, filter.position());
        filterId = filter.id();
    }

    @Test
    @Order(2)
    void findByAccountAndTable() {
        var filters = savedFilterRepo.findByAccountAndTable(account.id(), "members");
        assertEquals(1, filters.size());
        assertEquals("Active Members", filters.getFirst().name());
    }

    @Test
    @Order(3)
    void findByAccountAndTableEmpty() {
        assertTrue(savedFilterRepo
                .findByAccountAndTable(account.id(), "nonexistent")
                .isEmpty());
    }

    @Test
    @Order(4)
    void createMultipleAndOrder() {
        savedFilterRepo.create(account.id(), "members", "Inactive Members", "{\"status\":\"inactive\"}", 0);
        var filters = savedFilterRepo.findByAccountAndTable(account.id(), "members");
        assertEquals(2, filters.size());
        assertEquals("Inactive Members", filters.getFirst().name());
        assertEquals("Active Members", filters.get(1).name());
    }

    @Test
    @Order(5)
    void deleteOwnFilter() {
        assertTrue(savedFilterRepo.delete(filterId, account.id()));
        var filters = savedFilterRepo.findByAccountAndTable(account.id(), "members");
        assertEquals(1, filters.size());
    }

    @Test
    @Order(6)
    void deleteWrongAccount() {
        var filters = savedFilterRepo.findByAccountAndTable(account.id(), "members");
        int otherId = filters.getFirst().id();
        assertFalse(savedFilterRepo.delete(otherId, 99999));
        assertEquals(
                1,
                savedFilterRepo.findByAccountAndTable(account.id(), "members").size());
    }
}
