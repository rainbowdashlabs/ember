/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.equipment.repository;

import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.equipment.EquipmentTestSupport;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.inventory.entity.Inventory;
import dev.chojo.ember.feature.inventory.entity.InventoryArt;
import dev.chojo.ember.feature.inventory.entity.InventoryItem;
import dev.chojo.ember.feature.inventory.entity.InventoryItemMetadata;
import dev.chojo.ember.feature.inventory.entity.InventoryType;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EquipmentNeedRepositoryTest extends RepositoryTestBase {

    private static Station station;
    private static Account account;
    private static StationMember member;
    private static Inventory drawer;
    private static InventoryArt blue;
    private static InventoryItem radio;
    private static StationEvent dienst;

    @BeforeAll
    static void setup() {
        account = accountRepo.create("needrepo@test.example", "Need", "Repo");
        station = stationRepo.create("NeedRepoStation");
        member = stationMemberRepo.create(station.id(), account.id());
        drawer = inventoryRepo.create(station.id(), "NeedRepoFunk", InventoryType.INTERNAL, false, false);
        blue = artRepo.create(drawer.id(), "NeedRepoBlau", "", 0);
        radio = inventoryRepo.createItem(
                drawer.id(), "NR-01", "Funk blau", null, blue.id(), InventoryItemMetadata.empty(), null, null);
        dienst = EquipmentTestSupport.weekly(eventRepo, station.id(), "NeedRepoDienst", EquipmentTestSupport.SATURDAY);
    }

    @AfterAll
    static void cleanup() {
        stationRepo.delete(station.id());
        accountRepo.delete(account.id());
    }

    @Test
    void aLineIsWrittenReadAndChanged() {
        var need = equipmentNeedRepo.create(dienst.id(), null, null, blue.id(), null, 4, 60, 120);
        assertEquals(4, need.quantity());
        assertEquals(60, need.leadMinutes());
        assertTrue(need.forWholeSeries());
        assertEquals(blue.id(), need.target().artId());

        assertTrue(equipmentNeedRepo.update(need.id(), 6, 30, 30));
        var reread = equipmentNeedRepo.findById(need.id()).orElseThrow();
        assertEquals(6, reread.quantity());
        assertEquals(30, reread.trailMinutes());

        assertTrue(equipmentNeedRepo.findByEvent(dienst.id()).stream().anyMatch(l -> l.id() == need.id()));
        assertTrue(equipmentNeedRepo.findByStation(station.id()).stream().anyMatch(l -> l.id() == need.id()));
        assertTrue(equipmentNeedRepo.delete(need.id()));
        assertTrue(equipmentNeedRepo.findById(need.id()).isEmpty());
    }

    @Test
    void theLinesOfOneAppointmentKeepTheirOrder() {
        var event = EquipmentTestSupport.oneOff(
                eventRepo, station.id(), "NeedRepoOrder", EquipmentTestSupport.SATURDAY.plusDays(7));
        var first = equipmentNeedRepo.create(event.id(), null, radio.id(), null, null, 1, 0, 0);
        var second = equipmentNeedRepo.create(event.id(), null, null, null, drawer.id(), 2, 0, 0);
        assertEquals(List.of(first.id(), second.id()), ids(event.id()));

        equipmentNeedRepo.reorder(event.id(), List.of(second.id(), first.id()));
        assertEquals(List.of(second.id(), first.id()), ids(event.id()));

        assertEquals(2, equipmentNeedRepo.deleteByEvent(event.id()));
        assertTrue(equipmentNeedRepo.findByEvent(event.id()).isEmpty());
    }

    @Test
    void anEveningWritesALineOfItsOwnAndTakesItBack() {
        var event = EquipmentTestSupport.weekly(
                eventRepo, station.id(), "NeedRepoOneEvening", EquipmentTestSupport.SATURDAY);
        equipmentNeedRepo.create(event.id(), null, null, blue.id(), null, 2, 0, 0);
        var once = equipmentNeedRepo.create(event.id(), EquipmentTestSupport.SATURDAY, null, blue.id(), null, 9, 0, 0);
        assertFalse(once.forWholeSeries());
        assertEquals(EquipmentTestSupport.SATURDAY, once.eventDate());

        assertEquals(1, equipmentNeedRepo.deleteForDate(event.id(), EquipmentTestSupport.SATURDAY));
        assertEquals(1, equipmentNeedRepo.findByEvent(event.id()).size());
        equipmentNeedRepo.deleteByEvent(event.id());
    }

    @Test
    void aHandoverIsRecordedFoundAndClosed() {
        var event = EquipmentTestSupport.oneOff(
                eventRepo, station.id(), "NeedRepoHandover", EquipmentTestSupport.SATURDAY.plusDays(14));
        var need = equipmentNeedRepo.create(event.id(), null, radio.id(), null, null, 1, 0, 0);
        Instant from = EquipmentTestSupport.at(EquipmentTestSupport.SATURDAY.plusDays(14), 9);
        Instant to = EquipmentTestSupport.at(EquipmentTestSupport.SATURDAY.plusDays(14), 17);

        var handover = equipmentNeedRepo.recordHandover(
                need.id(), EquipmentTestSupport.SATURDAY.plusDays(14), radio.id(), from, to, member.id());
        assertTrue(handover.outstanding());
        assertNull(handover.returnedAt());

        assertEquals(
                1,
                equipmentNeedRepo
                        .findHandovers(event.id(), EquipmentTestSupport.SATURDAY.plusDays(14))
                        .size());
        assertEquals(
                1,
                equipmentNeedRepo
                        .findOpenHandovers(station.id(), from.minusSeconds(60), to.plusSeconds(60))
                        .size());

        assertTrue(equipmentNeedRepo.markReturned(handover.id(), event.id()));
        assertFalse(equipmentNeedRepo.markReturned(handover.id(), event.id()));
        assertTrue(equipmentNeedRepo
                .findOpenHandovers(station.id(), from.minusSeconds(60), to.plusSeconds(60))
                .isEmpty());
        equipmentNeedRepo.deleteByEvent(event.id());
    }

    @Test
    void aHandoverOfAnotherAppointmentIsNotClosedFromHere() {
        var mine = EquipmentTestSupport.oneOff(
                eventRepo, station.id(), "NeedRepoMine", EquipmentTestSupport.SATURDAY.plusDays(21));
        var other = EquipmentTestSupport.oneOff(
                eventRepo, station.id(), "NeedRepoOther", EquipmentTestSupport.SATURDAY.plusDays(28));
        var need = equipmentNeedRepo.create(mine.id(), null, radio.id(), null, null, 1, 0, 0);
        var handover = equipmentNeedRepo.recordHandover(
                need.id(),
                EquipmentTestSupport.SATURDAY.plusDays(21),
                radio.id(),
                EquipmentTestSupport.at(EquipmentTestSupport.SATURDAY.plusDays(21), 9),
                EquipmentTestSupport.at(EquipmentTestSupport.SATURDAY.plusDays(21), 17),
                null);

        assertFalse(equipmentNeedRepo.markReturned(handover.id(), other.id()));
        assertTrue(equipmentNeedRepo.markReturned(handover.id(), mine.id()));
        equipmentNeedRepo.deleteByEvent(mine.id());
    }

    @Test
    void handingTheSamePieceOverTwiceRewritesTheOneRow() {
        var event = EquipmentTestSupport.oneOff(
                eventRepo, station.id(), "NeedRepoTwice", EquipmentTestSupport.SATURDAY.plusDays(35));
        var need = equipmentNeedRepo.create(event.id(), null, radio.id(), null, null, 1, 0, 0);
        var day = EquipmentTestSupport.SATURDAY.plusDays(35);
        var first = equipmentNeedRepo.recordHandover(
                need.id(), day, radio.id(), EquipmentTestSupport.at(day, 9), EquipmentTestSupport.at(day, 17), null);
        var again = equipmentNeedRepo.recordHandover(
                need.id(), day, radio.id(), EquipmentTestSupport.at(day, 8), EquipmentTestSupport.at(day, 18), null);
        assertEquals(first.id(), again.id());
        assertEquals(1, equipmentNeedRepo.findHandovers(event.id(), day).size());
        equipmentNeedRepo.deleteByEvent(event.id());
    }

    private static List<Integer> ids(int eventId) {
        return equipmentNeedRepo.findByEvent(eventId).stream()
                .map(line -> line.id())
                .toList();
    }
}
