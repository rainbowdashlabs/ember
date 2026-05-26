/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.repository;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ApplicationSettingRepositoryTest extends RepositoryTestBase {

    @Test
    @Order(1)
    void getUnsetKeyReturnsEmpty() {
        assertTrue(applicationSettingRepo.get("nonexistent.key.xyz").isEmpty());
    }

    @Test
    @Order(2)
    void setAndGet() {
        applicationSettingRepo.set("test.setting", "hello");
        var value = applicationSettingRepo.get("test.setting");
        assertTrue(value.isPresent());
        assertEquals("hello", value.get());
    }

    @Test
    @Order(3)
    void setOverwritesExisting() {
        applicationSettingRepo.set("test.setting", "world");
        assertEquals("world", applicationSettingRepo.get("test.setting").orElseThrow());
    }

    @Test
    @Order(4)
    void getBooleanDefaultTrue() {
        assertTrue(applicationSettingRepo.getBoolean("boolean.unset.true", true));
    }

    @Test
    @Order(5)
    void getBooleanDefaultFalse() {
        assertFalse(applicationSettingRepo.getBoolean("boolean.unset.false", false));
    }

    @Test
    @Order(6)
    void setBooleanTrueAndGet() {
        applicationSettingRepo.setBoolean("test.boolean", true);
        assertTrue(applicationSettingRepo.getBoolean("test.boolean", false));
    }

    @Test
    @Order(7)
    void setBooleanFalseAndGet() {
        applicationSettingRepo.setBoolean("test.boolean", false);
        assertFalse(applicationSettingRepo.getBoolean("test.boolean", true));
    }

    @Test
    @Order(8)
    void multipleKeys() {
        applicationSettingRepo.set("key.a", "valueA");
        applicationSettingRepo.set("key.b", "valueB");
        assertEquals("valueA", applicationSettingRepo.get("key.a").orElseThrow());
        assertEquals("valueB", applicationSettingRepo.get("key.b").orElseThrow());
    }
}
