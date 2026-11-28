/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.station.transfer;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class LendingRowScopeTest {

    /** A request exported without a sound last day arrives with its first day as its last. */
    @Test
    void aRequestWithoutALastDayArrivesWithItsFirstDay() {
        assertEquals(
                "2026-05-02",
                LendingRowScope.withLastDay(row("2026-05-02", null)).get("requested_date_to"));

        var named = row("2026-05-02", "2026-05-09");
        assertSame(named, LendingRowScope.withLastDay(named), "a last day already given stays as it is");
        assertEquals(
                "2026-05-02",
                LendingRowScope.withLastDay(row("2026-05-02", "2026-04-28")).get("requested_date_to"),
                "a last day before the first is settled like a missing one");
    }

    private static Map<String, Object> row(String from, String to) {
        var row = new HashMap<String, Object>();
        row.put("requested_date_from", from);
        row.put("requested_date_to", to);
        return row;
    }
}
