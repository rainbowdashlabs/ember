/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.inventory.service;

import dev.chojo.ember.api.Refusal;
import dev.chojo.ember.api.RefusalResponse;
import dev.chojo.ember.api.TestSessions;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.feature.inventory.entity.InventoryItem;
import dev.chojo.ember.feature.inventory.entity.ItemOwner;
import dev.chojo.ember.feature.members.service.GuardianPolicy;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.repository.StationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.util.Optional;

import static dev.chojo.ember.api.TestSessions.MEMBER_ID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InventoryLossServiceTest {
    private static final int STATION = 3;
    private static final int ITEM = 40;
    private static final int WARD = 12;

    private InventoryService inventory;
    private SelfCheckService selfChecks;
    private GuardianPolicy guardians;
    private StationRepository stations;
    private InventoryLossService service;

    private static InventoryItem heldBy(Integer memberId) {
        return new InventoryItem(
                ITEM,
                5,
                "J-1",
                "Jacke",
                null,
                null,
                null,
                memberId,
                null,
                null,
                null,
                ItemOwner.STATION,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null);
    }

    private static Refusal refusalOf(Executable call) {
        return assertThrows(RefusalResponse.class, call).refusal();
    }

    private void noteRequired(boolean required) {
        var station = mock(Station.class);
        when(station.lossNoteRequired()).thenReturn(required);
        when(stations.findById(STATION)).thenReturn(Optional.of(station));
    }

    @BeforeEach
    void setup() {
        inventory = mock(InventoryService.class);
        selfChecks = mock(SelfCheckService.class);
        guardians = mock(GuardianPolicy.class);
        stations = mock(StationRepository.class);
        when(guardians.mayActFor(any(), anyInt())).thenAnswer(call -> {
            int member = call.getArgument(1);
            return member == MEMBER_ID || member == WARD;
        });
        when(inventory.markLost(anyInt(), any(), any())).thenReturn(Optional.of(heldBy(MEMBER_ID)));
        service = new InventoryLossService(inventory, selfChecks, guardians, stations);
    }

    @Test
    void aMemberReportsTheirOwnGearWithATrimmedNote() {
        when(inventory.findItemById(ITEM)).thenReturn(Optional.of(heldBy(MEMBER_ID)));

        service.markLost(TestSessions.member(STATION), ITEM, "  im Zug liegen gelassen ", null);

        verify(inventory).markLost(ITEM, "im Zug liegen gelassen", MEMBER_ID);
        verify(selfChecks, never()).recordLoss(anyInt(), anyInt(), anyInt(), eq(false), anyInt());
    }

    @Test
    void aGuardianReportsTheGearOfTheirWard() {
        when(inventory.findItemById(ITEM)).thenReturn(Optional.of(heldBy(WARD)));

        service.markLost(TestSessions.member(STATION), ITEM, "weg", null);

        verify(inventory).markLost(ITEM, "weg", MEMBER_ID);
    }

    @Test
    void gearOfSomebodyElseOrOfNobodyIsNotTheirsToReport() {
        when(inventory.findItemById(ITEM)).thenReturn(Optional.of(heldBy(99)));
        assertEquals(
                Refusal.LOSS_NOT_YOURS_TO_REPORT_FOR_THEM,
                refusalOf(() -> service.markLost(TestSessions.member(STATION), ITEM, "weg", null)));

        when(inventory.findItemById(ITEM)).thenReturn(Optional.of(heldBy(null)));
        assertEquals(
                Refusal.LOSS_NOT_YOURS_TO_REPORT,
                refusalOf(() -> service.markLost(TestSessions.member(STATION), ITEM, "weg", null)));
        verify(inventory, never()).markLost(anyInt(), any(), any());
    }

    @Test
    void theStationMayAskMembersForANote() {
        when(inventory.findItemById(ITEM)).thenReturn(Optional.of(heldBy(MEMBER_ID)));
        noteRequired(true);

        assertEquals(
                Refusal.LOSS_NEEDS_A_NOTE,
                refusalOf(() -> service.markLost(TestSessions.member(STATION), ITEM, "  ", null)));
    }

    @Test
    void whoeverLooksAfterTheGearReachesAllOfItWithoutANote() {
        when(inventory.findItemById(ITEM)).thenReturn(Optional.of(heldBy(null)));
        noteRequired(true);

        service.markLost(TestSessions.member(STATION, StationPermission.INVENTORY_EDIT), ITEM, null, null);

        verify(inventory).markLost(ITEM, null, null);
    }

    @Test
    void aLossDuringASelfCheckIsRecordedOnIt() {
        when(inventory.findItemById(ITEM)).thenReturn(Optional.of(heldBy(MEMBER_ID)));

        var lost = service.markLost(TestSessions.member(STATION, StationPermission.MEMBER_GUARDIAN), ITEM, null, 8);

        verify(selfChecks).recordLoss(8, STATION, MEMBER_ID, true, ITEM);
        assertEquals(ITEM, lost.id());
    }

    @Test
    void aPieceThatIsGoneOrCannotBeMarkedIsRefused() {
        when(inventory.findItemById(ITEM)).thenReturn(Optional.empty());
        assertEquals(
                Refusal.ITEM_NOT_HERE_ON_LOSS,
                refusalOf(() -> service.markLost(TestSessions.member(STATION), ITEM, null, null)));

        when(inventory.findItemById(ITEM)).thenReturn(Optional.of(heldBy(MEMBER_ID)));
        when(inventory.markLost(anyInt(), isNull(), isNull())).thenReturn(Optional.empty());
        assertEquals(
                Refusal.ITEM_NOT_MARKED_LOST,
                refusalOf(() -> service.markLost(TestSessions.member(STATION), ITEM, null, null)));
    }

    @Test
    void theNoteSettingIsReadAndWritten() {
        assertFalse(service.lossNoteRequired(STATION));

        noteRequired(true);
        assertTrue(service.requireLossNote(STATION, true));
        verify(stations).updateLossNoteRequired(STATION, true);
    }
}
