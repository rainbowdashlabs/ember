/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.equipment.service;

import dev.chojo.ember.event.DomainEventBus;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.equipment.EquipmentTestSupport;
import dev.chojo.ember.feature.equipment.entity.ClaimOrigin;
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

import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EquipmentAvailabilityServiceTest extends RepositoryTestBase {

    private static Station station;
    private static Station partner;
    private static Account account;
    private static StationMember member;
    private static Inventory drawer;
    private static InventoryArt blue;
    private static InventoryItem trailer;
    private static Inventory shed;
    private static EventServices services;
    private static EquipmentAvailabilityService availability;
    private static LendingRepository lendingRepo;

    @BeforeAll
    static void setup() {
        account = accountRepo.create("availsvc@test.example", "Avail", "Service");
        station = stationRepo.create("AvailSvcStation");
        partner = stationRepo.create("AvailSvcPartner");
        member = stationMemberRepo.create(station.id(), account.id());
        lendingRepo = new LendingRepository();

        drawer = inventoryRepo.create(station.id(), "AvailSvcFunk", InventoryType.INTERNAL, false, false);
        blue = artRepo.create(drawer.id(), "AvailSvcBlau", "", 0);
        for (int i = 1; i <= 6; i++) {
            inventoryRepo.createItem(
                    drawer.id(),
                    "ASV-B%02d".formatted(i),
                    "Funk blau",
                    null,
                    blue.id(),
                    InventoryItemMetadata.empty(),
                    null,
                    null);
        }

        shed = inventoryRepo.create(station.id(), "AvailSvcAnhaenger", InventoryType.INTERNAL, false);
        trailer = inventoryRepo.createItem(shed.id(), "ASV-T01", "Anhaenger", null, InventoryItemMetadata.empty());

        services = newEventServices(new DomainEventBus(Set.of()));
        availability = services.equipmentAvailability();
    }

    @AfterAll
    static void cleanup() {
        stationRepo.delete(station.id());
        stationRepo.delete(partner.id());
        accountRepo.delete(account.id());
    }

    private static Instant from(LocalDate date, int hour) {
        return EquipmentTestSupport.at(date, hour);
    }

    @Test
    void anEmptyDiaryLeavesEverythingFree() {
        var answer = availability.availability(
                station.id(),
                LineTarget.art(blue.id()),
                from(EquipmentTestSupport.SATURDAY.plusDays(200), 0),
                from(EquipmentTestSupport.SATURDAY.plusDays(201), 0));
        assertEquals(6, answer.stock());
        assertEquals(6, answer.free());
        assertFalse(answer.overClaimed());
        assertTrue(answer.claims().isEmpty());
    }

    @Test
    void twoAppointmentsClaimingTheSamePieceReportAnOverClaimRatherThanARefusal() {
        LocalDate day = EquipmentTestSupport.SATURDAY.plusDays(70);
        var one = EquipmentTestSupport.oneOff(eventRepo, station.id(), "AvailSvcMarsch", day);
        var two = EquipmentTestSupport.oneOff(eventRepo, station.id(), "AvailSvcZeltlager", day);
        equipmentNeedRepo.create(one.id(), null, trailer.id(), null, null, 1, 0, 0);
        equipmentNeedRepo.create(two.id(), null, trailer.id(), null, null, 1, 0, 0);

        var answer = availability.availability(
                station.id(), LineTarget.item(trailer.id()), from(day, 0), from(day.plusDays(1), 0));
        assertEquals(1, answer.stock());
        assertEquals(2, answer.claimed());
        assertEquals(-1, answer.free());
        assertTrue(answer.overClaimed());
        assertTrue(answer.claims().stream().anyMatch(c -> "AvailSvcMarsch".equals(c.label())));
        assertTrue(answer.claims().stream().anyMatch(c -> "AvailSvcZeltlager".equals(c.label())));

        equipmentNeedRepo.deleteByEvent(one.id());
        equipmentNeedRepo.deleteByEvent(two.id());
    }

    @Test
    void aClaimEndingMondayMorningDoesNotCollideWithOneStartingMondayAfternoon() {
        LocalDate sunday = EquipmentTestSupport.SATURDAY.plusDays(84);
        LocalDate monday = sunday.plusDays(1);
        var weekend = EquipmentTestSupport.oneOff(eventRepo, station.id(), "AvailSvcWochenende", sunday);
        equipmentNeedRepo.create(weekend.id(), null, trailer.id(), null, null, 1, 0, 16 * 60);

        var mondayMorning = availability.availability(
                station.id(), LineTarget.item(trailer.id()), from(monday, 6), from(monday, 8));
        assertEquals(1, mondayMorning.claimed());

        var mondayAfternoon = availability.availability(
                station.id(), LineTarget.item(trailer.id()), from(monday, 14), from(monday, 16));
        assertEquals(0, mondayAfternoon.claimed());
        assertEquals(1, mondayAfternoon.free());

        equipmentNeedRepo.deleteByEvent(weekend.id());
    }

    @Test
    void aWeeklySeriesClaimsEveryEveningItProduces() {
        var dienst =
                EquipmentTestSupport.weekly(eventRepo, station.id(), "AvailSvcDienst", EquipmentTestSupport.SATURDAY);
        equipmentNeedRepo.create(dienst.id(), null, null, blue.id(), null, 4, 60, 60);

        for (int week = 0; week < 3; week++) {
            LocalDate evening = EquipmentTestSupport.SATURDAY.plusWeeks(week);
            var answer = availability.availability(
                    station.id(), LineTarget.art(blue.id()), from(evening, 19), from(evening, 21));
            assertEquals(4, answer.claimed(), "week " + week);
            assertEquals(2, answer.free());
        }

        var wednesday = EquipmentTestSupport.SATURDAY.plusDays(4);
        assertEquals(
                0,
                availability
                        .availability(station.id(), LineTarget.art(blue.id()), from(wednesday, 19), from(wednesday, 21))
                        .claimed());

        equipmentNeedRepo.deleteByEvent(dienst.id());
    }

    @Test
    void oneEveningOverridesItsOwnLineWithoutTouchingTheSeries() {
        var dienst = EquipmentTestSupport.weekly(
                eventRepo, station.id(), "AvailSvcUeberschreiben", EquipmentTestSupport.SATURDAY);
        equipmentNeedRepo.create(dienst.id(), null, null, blue.id(), null, 2, 0, 0);
        LocalDate special = EquipmentTestSupport.SATURDAY.plusWeeks(1);
        equipmentNeedRepo.create(dienst.id(), special, null, blue.id(), null, 5, 0, 0);

        assertEquals(
                2,
                availability
                        .availability(
                                station.id(),
                                LineTarget.art(blue.id()),
                                from(EquipmentTestSupport.SATURDAY, 19),
                                from(EquipmentTestSupport.SATURDAY, 21))
                        .claimed());
        assertEquals(
                5,
                availability
                        .availability(station.id(), LineTarget.art(blue.id()), from(special, 19), from(special, 21))
                        .claimed());

        equipmentNeedRepo.deleteByEvent(dienst.id());
    }

    @Test
    void aCancelledAppointmentClaimsNothing() {
        LocalDate day = EquipmentTestSupport.SATURDAY.plusDays(98);
        var event = EquipmentTestSupport.oneOff(eventRepo, station.id(), "AvailSvcAbgesagt", day);
        equipmentNeedRepo.create(event.id(), null, trailer.id(), null, null, 1, 0, 0);
        assertEquals(
                1,
                availability
                        .availability(
                                station.id(), LineTarget.item(trailer.id()), from(day, 0), from(day.plusDays(1), 0))
                        .claimed());

        eventRepo.cancelEvent(event.id(), "Wetter");
        assertEquals(
                0,
                availability
                        .availability(
                                station.id(), LineTarget.item(trailer.id()), from(day, 0), from(day.plusDays(1), 0))
                        .claimed());
        equipmentNeedRepo.deleteByEvent(event.id());
    }

    @Test
    void aLoanAndABlockClaimTheSameStockTheNeedsDo() {
        LocalDate day = EquipmentTestSupport.SATURDAY.plusDays(112);
        var request = lendingRepo.createRequest(
                partner.uid(), station.uid(), day, day, member.id(), null, null, "Nachbarwache");
        lendingRepo.addRequestItem(request.id(), drawer.id(), null, blue.id(), 2, null);
        lendingRepo.updateRequestStatus(request.id(), LendingStatus.APPROVED);

        var withLoan = availability.availability(station.id(), LineTarget.art(blue.id()), from(day, 10), from(day, 12));
        assertEquals(2, withLoan.claimed());
        assertTrue(withLoan.claims().stream().anyMatch(c -> c.origin() == ClaimOrigin.LOAN));

        var block = lendingRepo.createBlock(station.id(), drawer.id(), null, day, day, "Inspektion");
        var withBlock =
                availability.availability(station.id(), LineTarget.art(blue.id()), from(day, 10), from(day, 12));
        assertTrue(withBlock.claims().stream().anyMatch(c -> c.origin() == ClaimOrigin.BLOCK));
        assertTrue(withBlock.overClaimed());

        lendingRepo.deleteBlock(block.id(), station.id());
        lendingRepo.updateRequestStatus(request.id(), LendingStatus.CLOSED);
    }

    @Test
    void aPieceSetAsideByNameLeavesTheFreeList() {
        LocalDate day = EquipmentTestSupport.SATURDAY.plusDays(126);
        var pieces = equipmentAvailabilityRepo.piecesOf(station.id(), LineTarget.art(blue.id()));
        var request = lendingRepo.createRequest(
                partner.uid(), station.uid(), day, day, member.id(), null, null, "Nachbarwache");
        var line = lendingRepo.addRequestItem(request.id(), drawer.id(), null, blue.id(), 1, null);
        lendingRepo.assignItem(line.id(), pieces.getFirst());
        lendingRepo.updateRequestStatus(request.id(), LendingStatus.LENT);

        var free = availability.freePieces(station.id(), LineTarget.art(blue.id()), from(day, 10), from(day, 12));
        assertFalse(free.contains(pieces.getFirst()));
        assertEquals(pieces.size() - 1, free.size());
        lendingRepo.updateRequestStatus(request.id(), LendingStatus.CLOSED);
    }

    @Test
    void aHandedOverPieceReplacesTheLooseClaimItCameFrom() {
        LocalDate day = EquipmentTestSupport.SATURDAY.plusDays(140);
        var event = EquipmentTestSupport.oneOff(eventRepo, station.id(), "AvailSvcUebergabe", day);
        var need = equipmentNeedRepo.create(event.id(), null, null, blue.id(), null, 2, 0, 0);
        var pieces = equipmentAvailabilityRepo.piecesOf(station.id(), LineTarget.art(blue.id()));
        equipmentNeedRepo.recordHandover(need.id(), day, pieces.getFirst(), from(day, 9), from(day, 17), member.id());

        var answer = availability.availability(station.id(), LineTarget.art(blue.id()), from(day, 10), from(day, 12));
        assertEquals(2, answer.claimed());
        assertTrue(answer.claims().stream().anyMatch(c -> c.firm() && c.quantity() == 1));
        assertTrue(answer.claims().stream().anyMatch(c -> !c.firm() && c.quantity() == 1));
        equipmentNeedRepo.deleteByEvent(event.id());
    }

    @Test
    void aLineAskingWhatIsLeftDoesNotCountItself() {
        LocalDate day = EquipmentTestSupport.SATURDAY.plusDays(154);
        var event = EquipmentTestSupport.oneOff(eventRepo, station.id(), "AvailSvcSelbst", day);
        var need = equipmentNeedRepo.create(event.id(), null, null, blue.id(), null, 3, 0, 0);

        assertEquals(
                3,
                availability
                        .availability(station.id(), LineTarget.art(blue.id()), from(day, 10), from(day, 12))
                        .claimed());
        assertEquals(
                0,
                availability
                        .availability(station.id(), LineTarget.art(blue.id()), from(day, 10), from(day, 12), need.id())
                        .claimed());
        equipmentNeedRepo.deleteByEvent(event.id());
    }

    @Test
    void anAppointmentInABreakProducesNoEvening() {
        var dienst =
                EquipmentTestSupport.weekly(eventRepo, station.id(), "AvailSvcFerien", EquipmentTestSupport.SATURDAY);
        equipmentNeedRepo.create(dienst.id(), null, trailer.id(), null, null, 1, 0, 0);
        LocalDate evening = EquipmentTestSupport.SATURDAY.plusWeeks(4);
        var pause =
                eventBreakRepo.create(station.id(), "AvailSvcSommerferien", evening.minusDays(1), evening.plusDays(1));

        assertEquals(
                0,
                availability
                        .availability(station.id(), LineTarget.item(trailer.id()), from(evening, 19), from(evening, 21))
                        .claimed());

        eventBreakRepo.delete(pause.id());
        equipmentNeedRepo.deleteByEvent(dienst.id());
    }

    @Test
    void askingAboutSomethingThatIsGoneIsRefused() {
        assertThrows(
                IllegalArgumentException.class,
                () -> availability.availability(
                        station.id(),
                        LineTarget.art(-1),
                        from(EquipmentTestSupport.SATURDAY, 10),
                        from(EquipmentTestSupport.SATURDAY, 12)));
        assertTrue(availability.resolve(LineTarget.art(-1)).isEmpty());
    }
}
