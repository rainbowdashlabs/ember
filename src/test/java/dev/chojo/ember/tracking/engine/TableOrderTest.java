/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.tracking.engine;

import dev.chojo.ember.tracking.DataTrackingLoader;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies the derived export order respects FK dependencies between the TRACKED tables.
 */
class TableOrderTest {

    private static List<String> order;

    @BeforeAll
    static void setup() throws IOException {
        order = TableOrder.topological(DataTrackingLoader.loadFromClasspath());
    }

    @Test
    void orderIsNonEmpty() {
        assertFalse(order.isEmpty());
    }

    @Test
    void stationComesBeforeStationMember() {
        assertTrue(order.indexOf("station") < order.indexOf("station_member"));
    }

    /** The account is scoped through the station member, yet the member's foreign key still orders it first. */
    @Test
    void accountComesBeforeStationMember() {
        assertTrue(order.indexOf("account") < order.indexOf("station_member"));
    }

    @Test
    void memberGroupComesBeforeMemberGroupEntry() {
        assertTrue(order.indexOf("member_group") < order.indexOf("member_group_entry"));
    }

    @Test
    void boardLaneComesBeforeBoardTicket() {
        assertTrue(order.indexOf("board_lane") < order.indexOf("board_ticket"));
    }
}
