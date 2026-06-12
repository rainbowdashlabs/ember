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
        // station_member has FK station_id → station.id
        assertTrue(order.indexOf("station") < order.indexOf("station_member"));
    }

    @Test
    void accountComesBeforeStationMember() {
        // station_member has FK account_id → account.id; account is reached via customScope
        // through station_member, but the topological order still places it before station_member
        // because of the FK declaration.
        assertTrue(order.indexOf("account") < order.indexOf("station_member"));
    }

    @Test
    void memberGroupComesBeforeMemberGroupEntry() {
        // member_group_entry has FK group_id → member_group.id
        assertTrue(order.indexOf("member_group") < order.indexOf("member_group_entry"));
    }

    @Test
    void boardLaneComesBeforeBoardTicket() {
        // board_ticket has FK lane_id → board_lane.id
        assertTrue(order.indexOf("board_lane") < order.indexOf("board_ticket"));
    }
}
