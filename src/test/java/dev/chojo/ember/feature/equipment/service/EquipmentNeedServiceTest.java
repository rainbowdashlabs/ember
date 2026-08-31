/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.equipment.service;

import dev.chojo.ember.event.DomainEventBus;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.equipment.EquipmentTestSupport;
import dev.chojo.ember.feature.federation.entity.LendingStatus;
import dev.chojo.ember.feature.federation.repository.LendingRepository;
import dev.chojo.ember.feature.inventory.entity.Inventory;
import dev.chojo.ember.feature.inventory.entity.InventoryArt;
import dev.chojo.ember.feature.inventory.entity.InventoryItem;
import dev.chojo.ember.feature.inventory.entity.InventoryItemMetadata;
import dev.chojo.ember.feature.inventory.entity.InventoryType;
import dev.chojo.ember.feature.inventory.entity.LineTarget;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EquipmentNeedServiceTest extends RepositoryTestBase {

    private static Station station;
    private static Station partner;
    private static Account account;
    private static StationMember member;
    private static Inventory drawer;
    private static Inventory foreign;
    private static InventoryArt blue;
    private static InventoryItem trailer;
    private static EventServices services;
    private static EquipmentNeedService needs;
    private static LendingRepository lendingRepo;

    @BeforeAll
    static void setup() {
        account = accountRepo.create("needsvc@test.example", "Need", "Service");
        station = stationRepo.create("NeedSvcStation");
        partner = stationRepo.create("NeedSvcPartner");
        member = stationMemberRepo.create(station.id(), account.id());
        lendingRepo = new LendingRepository();

        drawer = inventoryRepo.create(station.id(), "NeedSvcFunk", InventoryType.INTERNAL, false, false);
        blue = artRepo.create(drawer.id(), "NeedSvcBlau", "", 0);
        for (int i = 1; i <= 4; i++) {
            inventoryRepo.createItem(
                    drawer.id(),
                    "NSV-B%02d".formatted(i),
                    "Funk blau",
                    null,
                    blue.id(),
                    InventoryItemMetadata.empty(),
                    null,
                    null);
        }
        trailer = inventoryRepo.createItem(drawer.id(), "NSV-T01", "Anhaenger", null, InventoryItemMetadata.empty());
        foreign = inventoryRepo.create(partner.id(), "NeedSvcFremd", InventoryType.INTERNAL, false);

        services = newEventServices(new DomainEventBus(Set.of()));
        needs = services.equipmentNeeds();
    }

    @AfterAll
    static void cleanup() {
        stationRepo.delete(station.id());
        stationRepo.delete(partner.id());
        accountRepo.delete(account.id());
    }

    @Test
    void aLineIsWrittenChangedReorderedAndTakenOff() {
        var event = EquipmentTestSupport.oneOff(
                eventRepo, station.id(), "NeedSvcCrud", EquipmentTestSupport.SATURDAY.plusDays(300));
        var counted = needs.add(event.id(), station.id(), null, LineTarget.art(blue.id()), 3, 60, 120);
        assertEquals(3, counted.quantity());
        var named = needs.add(event.id(), station.id(), null, LineTarget.item(trailer.id()), 7, 0, 0);
        assertEquals(1, named.quantity(), "A named piece is one piece however many were asked for");

        assertTrue(needs.update(counted.id(), 5, 30, 30));
        assertEquals(5, needs.findById(counted.id()).orElseThrow().quantity());

        needs.reorder(event.id(), List.of(named.id(), counted.id()));
        assertEquals(
                List.of(named.id(), counted.id()),
                needs.findByEvent(event.id()).stream().map(l -> l.id()).toList());

        assertTrue(needs.delete(named.id()));
        assertEquals(1, needs.findByEvent(event.id()).size());
        equipmentNeedRepo.deleteByEvent(event.id());
    }

    @Test
    void whatCannotBeAskedForIsRefused() {
        var event = EquipmentTestSupport.oneOff(
                eventRepo, station.id(), "NeedSvcRefusals", EquipmentTestSupport.SATURDAY.plusDays(310));

        assertThrows(
                IllegalArgumentException.class,
                () -> needs.add(event.id(), station.id(), null, LineTarget.art(blue.id()), 0, 0, 0));
        assertThrows(
                IllegalArgumentException.class,
                () -> needs.add(event.id(), station.id(), null, LineTarget.art(blue.id()), 1, -1, 0));
        assertThrows(
                IllegalArgumentException.class,
                () -> needs.add(event.id(), station.id(), null, LineTarget.art(blue.id()), 1, 0, 999_999));
        assertThrows(
                IllegalArgumentException.class,
                () -> needs.add(event.id(), station.id(), null, LineTarget.inventory(foreign.id()), 1, 0, 0));

        var line = needs.add(event.id(), station.id(), null, LineTarget.art(blue.id()), 1, 0, 0);
        assertThrows(
                IllegalArgumentException.class,
                () -> needs.add(event.id(), station.id(), null, LineTarget.art(blue.id()), 1, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> needs.update(line.id(), 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> needs.update(line.id(), 1, 0, -5));
        assertThrows(IllegalArgumentException.class, () -> needs.update(-1, 1, 0, 0));

        var named = needs.add(event.id(), station.id(), null, LineTarget.item(trailer.id()), 1, 0, 0);
        assertThrows(IllegalArgumentException.class, () -> needs.update(named.id(), 2, 0, 0));
        equipmentNeedRepo.deleteByEvent(event.id());
    }

    @Test
    void oneEveningWritesALineOfItsOwn() {
        var dienst =
                EquipmentTestSupport.weekly(eventRepo, station.id(), "NeedSvcDienst", EquipmentTestSupport.SATURDAY);
        needs.add(dienst.id(), station.id(), null, LineTarget.art(blue.id()), 2, 0, 0);
        LocalDate special = EquipmentTestSupport.SATURDAY.plusWeeks(2);
        needs.add(dienst.id(), station.id(), special, LineTarget.item(trailer.id()), 1, 0, 0);

        assertEquals(
                1, needs.coverage(dienst.id(), EquipmentTestSupport.SATURDAY).size());
        assertEquals(2, needs.coverage(dienst.id(), special).size());
        equipmentNeedRepo.deleteByEvent(dienst.id());
    }

    @Test
    void aLineSaysWhatItStillMisses() {
        LocalDate day = EquipmentTestSupport.SATURDAY.plusDays(320);
        var event = EquipmentTestSupport.oneOff(eventRepo, station.id(), "NeedSvcDeckung", day);
        var line = needs.add(event.id(), station.id(), null, LineTarget.art(blue.id()), 6, 0, 0);

        var coverage = needs.coverage(event.id(), day).getFirst();
        assertEquals("NeedSvcBlau", coverage.label());
        assertEquals(4, coverage.own());
        assertEquals(0, coverage.borrowed());
        assertEquals(0, coverage.outstanding());
        assertEquals(2, coverage.missing());
        assertFalse(coverage.covered());

        var request = lendingRepo.createRequest(
                station.uid(), partner.uid(), day, day, member.id(), event.id(), day, "NeedSvcDeckung");
        lendingRepo.addRequestItem(request.id(), foreign.id(), null, null, 2, line.id());
        var asked = needs.coverage(event.id(), day).getFirst();
        assertEquals(2, asked.outstanding());
        assertEquals(0, asked.missing());
        assertTrue(asked.covered());

        lendingRepo.updateRequestStatus(request.id(), LendingStatus.CLOSED);
        equipmentNeedRepo.deleteByEvent(event.id());
    }

    @Test
    void anOverClaimIsNamedBesideTheLine() {
        LocalDate day = EquipmentTestSupport.SATURDAY.plusDays(330);
        var mine = EquipmentTestSupport.oneOff(eventRepo, station.id(), "NeedSvcMeins", day);
        var other = EquipmentTestSupport.oneOff(eventRepo, station.id(), "NeedSvcAnderes", day);
        needs.add(mine.id(), station.id(), null, LineTarget.item(trailer.id()), 1, 0, 0);
        needs.add(other.id(), station.id(), null, LineTarget.item(trailer.id()), 1, 0, 0);

        var coverage = needs.coverage(mine.id(), day).getFirst();
        assertTrue(coverage.overClaim().stream().anyMatch(c -> "NeedSvcAnderes".equals(c.label())));
        assertEquals(1, coverage.missing());

        equipmentNeedRepo.deleteByEvent(mine.id());
        equipmentNeedRepo.deleteByEvent(other.id());
    }

    @Test
    void aPieceIsHandedOverAndComesBack() {
        LocalDate day = EquipmentTestSupport.SATURDAY.plusDays(340);
        var event = EquipmentTestSupport.oneOff(eventRepo, station.id(), "NeedSvcUebergabe", day);
        var line = needs.add(event.id(), station.id(), null, LineTarget.item(trailer.id()), 1, 60, 60);

        var handover = needs.handOver(line.id(), day, trailer.id(), member.id());
        assertEquals(trailer.id(), handover.itemId());
        assertTrue(handover.outstanding());
        assertEquals(1, needs.handovers(event.id(), day).size());

        assertTrue(needs.handBack(handover.id(), event.id()));
        assertFalse(needs.handBack(handover.id(), event.id()));
        equipmentNeedRepo.deleteByEvent(event.id());
    }

    @Test
    void aHandoverOfSomebodyElsesGearIsRefused() {
        LocalDate day = EquipmentTestSupport.SATURDAY.plusDays(350);
        var event = EquipmentTestSupport.oneOff(eventRepo, station.id(), "NeedSvcFremdUebergabe", day);
        var line = needs.add(event.id(), station.id(), null, LineTarget.item(trailer.id()), 1, 0, 0);
        var strangerItem =
                inventoryRepo.createItem(foreign.id(), "NSV-F01", "Fremd", null, InventoryItemMetadata.empty());

        assertThrows(
                IllegalArgumentException.class, () -> needs.handOver(line.id(), day, strangerItem.id(), member.id()));
        assertThrows(IllegalArgumentException.class, () -> needs.handOver(-1, day, trailer.id(), member.id()));
        assertThrows(IllegalArgumentException.class, () -> needs.coverage(-1, day));
        equipmentNeedRepo.deleteByEvent(event.id());
    }
}
