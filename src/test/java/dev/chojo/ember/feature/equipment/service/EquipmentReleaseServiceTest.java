/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.equipment.service;

import dev.chojo.ember.conf.file.elements.Api;
import dev.chojo.ember.event.DomainEventBus;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.equipment.EquipmentTestSupport;
import dev.chojo.ember.feature.federation.entity.CapabilityType;
import dev.chojo.ember.feature.federation.entity.Direction;
import dev.chojo.ember.feature.federation.entity.LendingStatus;
import dev.chojo.ember.feature.federation.repository.FederationRepository;
import dev.chojo.ember.feature.federation.repository.LendingRepository;
import dev.chojo.ember.feature.federation.service.FederationService;
import dev.chojo.ember.feature.inventory.entity.Inventory;
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
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EquipmentReleaseServiceTest extends RepositoryTestBase {

    private static Station station;
    private static Station partner;
    private static Account account;
    private static StationMember member;
    private static Inventory drawer;
    private static InventoryItem trailer;
    private static EventServices services;
    private static EquipmentReleaseService release;
    private static LendingRepository lendingRepo;

    @BeforeAll
    static void setup() {
        account = accountRepo.create("release@test.example", "Release", "Service");
        station = stationRepo.create("ReleaseStation");
        partner = stationRepo.create("ReleasePartner");
        member = stationMemberRepo.create(station.id(), account.id());
        lendingRepo = new LendingRepository();

        drawer = inventoryRepo.create(station.id(), "ReleaseFunk", InventoryType.INTERNAL, false);
        trailer = inventoryRepo.createItem(drawer.id(), "REL-01", "Anhaenger", null, InventoryItemMetadata.empty());

        var federationRepo = new FederationRepository();
        var federationService = new FederationService(federationRepo, stationRepo, new Api());
        var keyPair = federationService.generateKeyPair();
        federationService.acceptInvite(
                station.id(), partner.id(), federationService.encodePublicKey(keyPair), null, null);
        int partnership = federationService.findPartners(station.id()).stream()
                .filter(p -> partner.uid().equals(p.partnerStationId()))
                .findFirst()
                .orElseThrow()
                .id();
        federationService.setCapability(partnership, CapabilityType.INVENTORY_LEND, Direction.IMPORT, true);

        services = newEventServices(new DomainEventBus(Set.of()));
        release = new EquipmentReleaseService(equipmentNeedRepo, services.lending());
    }

    @AfterAll
    static void cleanup() {
        stationRepo.delete(station.id());
        stationRepo.delete(partner.id());
        accountRepo.delete(account.id());
    }

    @Test
    void deletingAnAppointmentReleasesItsLinesAndWithdrawsItsRequests() {
        LocalDate day = EquipmentTestSupport.SATURDAY.plusDays(400);
        var event = EquipmentTestSupport.oneOff(eventRepo, station.id(), "ReleaseGeloescht", day);
        var line =
                services.equipmentNeeds().add(event.id(), station.id(), null, LineTarget.item(trailer.id()), 1, 0, 0);
        var request = lendingRepo.createRequest(
                station.uid(), partner.uid(), day, day, member.id(), event.id(), day, "ReleaseGeloescht");
        lendingRepo.addRequestItem(request.id(), null, null, null, 1, line.id());

        release.release(event.id(), station.id());

        assertTrue(equipmentNeedRepo.findByEvent(event.id()).isEmpty());
        assertEquals(
                LendingStatus.DECLINED,
                lendingRepo.findRequestById(request.id()).orElseThrow().status());
    }

    @Test
    void cancellingWithdrawsTheRequestsAndKeepsTheLines() {
        LocalDate day = EquipmentTestSupport.SATURDAY.plusDays(410);
        var event = EquipmentTestSupport.oneOff(eventRepo, station.id(), "ReleaseAbgesagt", day);
        var line =
                services.equipmentNeeds().add(event.id(), station.id(), null, LineTarget.item(trailer.id()), 1, 0, 0);
        var request = lendingRepo.createRequest(
                station.uid(), partner.uid(), day, day, member.id(), event.id(), day, "ReleaseAbgesagt");
        lendingRepo.addRequestItem(request.id(), null, null, null, 1, line.id());

        release.withdrawRequests(event.id(), station.id());

        assertEquals(1, equipmentNeedRepo.findByEvent(event.id()).size());
        assertEquals(
                LendingStatus.DECLINED,
                lendingRepo.findRequestById(request.id()).orElseThrow().status());
        equipmentNeedRepo.deleteByEvent(event.id());
    }

    @Test
    void anAppointmentWithNothingToReleaseIsQuiet() {
        var event = EquipmentTestSupport.oneOff(
                eventRepo, station.id(), "ReleaseLeer", EquipmentTestSupport.SATURDAY.plusDays(420));
        release.release(event.id(), station.id());
        release.withdrawRequests(event.id(), station.id());
        assertTrue(equipmentNeedRepo.findByEvent(event.id()).isEmpty());
    }
}
