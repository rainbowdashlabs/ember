/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.federation.service;

import dev.chojo.ember.feature.federation.entity.LendingRequest;
import dev.chojo.ember.feature.federation.entity.LendingRequestItem;
import dev.chojo.ember.feature.federation.repository.LendingRepository;
import dev.chojo.ember.feature.inventory.entity.InventoryType;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A lending request that arrives with a moved station where its partner's copy already is, merged into
 * that copy line by line.
 */
class LendingUidClashesTest extends RepositoryTestBase {

    /**
     * The run writes a line only once what it names has arrived, so the arrived lines can stand in
     * another order here than at the source. They are paired with the kept copy's lines in the order
     * the source gave them, not in the order they were written here.
     */
    @Test
    void linesWrittenOutOfOrderJoinTheLinesTheyHadAtTheSource() {
        Station borrower = stationRepo.create("Clash Borrower");
        Station lender = stationRepo.create("Clash Lender");
        int gear = inventoryRepo
                .create(lender.id(), "Geräte", InventoryType.INTERNAL, false)
                .id();
        int radio = inventoryRepo
                .createItem(gear, "HRT-C", "Funkgerät C", null, null)
                .id();
        int pump = inventoryRepo.createItem(gear, "TS-C", "Pumpe C", null, null).id();
        var lending = new LendingRepository();
        UUID uid = UUID.randomUUID();
        var kept = request(lending, uid, borrower, lender);
        lending.addRequestItem(kept.id(), null, null, null, 1, null);
        lending.addRequestItem(kept.id(), null, null, null, 1, null);
        lending.labelItems(kept.id(), List.of("Funk", "Pumpe"));
        UUID standIn = UUID.randomUUID();
        var arrived = request(lending, standIn, borrower, lender);
        var secondAtTheSource = lending.addRequestItem(arrived.id(), gear, pump, null, 1, null);
        var firstAtTheSource = lending.addRequestItem(arrived.id(), gear, radio, null, 1, null);

        int merged = new LendingUidClashes(lending)
                .merge(Map.of(standIn, uid), Map.of(firstAtTheSource.id(), 101, secondAtTheSource.id(), 102));

        assertEquals(1, merged);
        var lines = lending.findItemsByRequest(kept.id());
        assertEquals(
                List.of("Funk", "Pumpe"),
                lines.stream().map(LendingRequestItem::label).toList());
        assertEquals(radio, lines.get(0).itemId());
        assertEquals(pump, lines.get(1).itemId());
        assertTrue(lending.findRequestByUid(standIn).isEmpty(), "the arrived copy goes");
        stationRepo.delete(borrower.id());
        stationRepo.delete(lender.id());
    }

    private static LendingRequest request(LendingRepository lending, UUID uid, Station borrower, Station lender) {
        return lending.createRequest(
                uid,
                borrower.uid(),
                lender.uid(),
                LocalDate.now(),
                LocalDate.now().plusDays(2),
                null,
                null,
                null,
                "Übung");
    }
}
