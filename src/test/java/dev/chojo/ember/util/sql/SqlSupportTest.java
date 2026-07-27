/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.util.sql;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SqlSupportTest {

    @Test
    void aliasPrefixesPlainColumns() {
        assertEquals("e.id, e.name, e.created_at", SqlSupport.alias("e", "id, name, created_at"));
    }

    @Test
    void aliasKeepsExpressionsUntouched() {
        assertEquals(
                "e.id, EXISTS(SELECT 1 FROM x WHERE a IN (1, 2)) AS restricted, e.name",
                SqlSupport.alias("e", "id, EXISTS(SELECT 1 FROM x WHERE a IN (1, 2)) AS restricted, name"));
    }

    @Test
    void aliasKeepsQualifiedAndAliasedColumnsUntouched() {
        assertEquals("e.id, other.name, cnt AS total", SqlSupport.alias("e", "id, other.name, cnt AS total"));
    }

    @Test
    void aliasHandlesMultilineConstants() {
        assertEquals("e.id, e.name", SqlSupport.alias("e", "id,\n        name"));
    }
}
