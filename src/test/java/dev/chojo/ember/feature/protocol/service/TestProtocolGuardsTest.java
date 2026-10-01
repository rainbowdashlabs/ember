/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.protocol.service;

import dev.chojo.ember.api.ApiServer;
import dev.chojo.ember.api.TestSessions;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.api.refusal.GeneralRefusal;
import dev.chojo.ember.api.refusal.Refusal;
import dev.chojo.ember.api.refusal.RefusalResponse;
import dev.chojo.ember.api.refusal.TestProtocolRefusal;
import dev.chojo.ember.feature.protocol.entity.TestProtocol;
import dev.chojo.ember.feature.protocol.entity.TestProtocolRun;
import io.javalin.http.Context;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TestProtocolGuardsTest {
    private static final int STATION = 3;

    private TestProtocolService service;
    private TestProtocolGuards guards;
    private Context ctx;

    private static Refusal refusalOf(Executable call) {
        return assertThrows(RefusalResponse.class, call).refusal();
    }

    @BeforeEach
    void setup() {
        service = mock(TestProtocolService.class);
        guards = new TestProtocolGuards(service);
        ctx = mock(Context.class);
        when(ctx.<UserSession>attribute(ApiServer.ATTR_SESSION)).thenReturn(TestSessions.member(STATION));
        when(service.findProtocol(anyInt())).thenReturn(Optional.empty());
        when(service.findRun(anyInt())).thenReturn(Optional.empty());
        when(service.findSectionStation(anyInt())).thenReturn(Optional.empty());
        when(service.findItemStation(anyInt())).thenReturn(Optional.empty());
    }

    @Test
    void aProtocolAndARunOfTheStationAreHandedOutAndOthersAreNotHere() {
        var protocol = mock(TestProtocol.class);
        when(protocol.stationId()).thenReturn(STATION);
        when(service.findProtocol(1)).thenReturn(Optional.of(protocol));
        var foreignRun = mock(TestProtocolRun.class);
        when(foreignRun.stationId()).thenReturn(9);
        when(service.findRun(2)).thenReturn(Optional.of(foreignRun));

        assertSame(protocol, guards.requireProtocol(ctx, 1));
        assertEquals(GeneralRefusal.NOT_HERE_OR_NOT_YOURS, refusalOf(() -> guards.requireRun(ctx, 2)));
        assertEquals(GeneralRefusal.NOT_HERE_OR_NOT_YOURS, refusalOf(() -> guards.requireProtocol(ctx, 3)));
    }

    @Test
    void sectionsAndItemsAreTheStationsWhenTheirProtocolIs() {
        when(service.findSectionStation(1)).thenReturn(Optional.of(STATION));
        when(service.findSectionStation(2)).thenReturn(Optional.of(9));
        when(service.findItemStation(3)).thenReturn(Optional.of(STATION));

        assertDoesNotThrow(() -> guards.requireSection(ctx, 1));
        assertDoesNotThrow(() -> guards.requireItem(ctx, 3));
        assertEquals(GeneralRefusal.NOT_YOURS_TO_OPEN, refusalOf(() -> guards.requireSection(ctx, 2)));
        assertEquals(TestProtocolRefusal.PROTOCOL_SECTION_NOT_HERE, refusalOf(() -> guards.requireSection(ctx, 5)));
        assertEquals(TestProtocolRefusal.PROTOCOL_ITEM_NOT_HERE, refusalOf(() -> guards.requireItem(ctx, 5)));
    }
}
