/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.system.service;

import dev.chojo.ember.feature.equipment.entity.EquipmentNeed;
import dev.chojo.ember.feature.equipment.repository.EquipmentNeedRepository;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.events.repository.EventRepository;
import dev.chojo.ember.feature.inventory.entity.Inventory;
import dev.chojo.ember.feature.inventory.entity.InventoryArt;
import dev.chojo.ember.feature.inventory.repository.InventoryArtRepository;
import dev.chojo.ember.feature.inventory.repository.InventoryRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;

/**
 * What the demo station's appointments need, so the panel has something to say on the first look.
 *
 * <p>Two shapes, because they read differently. The weekly Übung asks for four of a kind and gets
 * them, which is the ordinary evening. The Kreiswettbewerb asks for more radios than the station has,
 * which is what the borrowing screen exists for.
 *
 * <p>It runs after the inventory and the appointments both exist, which is what the band says.
 */
@Singleton
public class DemoEquipmentSeeder implements DemoPerStationSeeder {

    private static final Logger log = LoggerFactory.getLogger(DemoEquipmentSeeder.class);

    /** A day either way, the same default a line written by hand starts with. */
    private static final int LEAD_MINUTES = EquipmentNeed.DEFAULT_LEAD_MINUTES;

    private final EquipmentNeedRepository needRepository;
    private final EventRepository eventRepository;
    private final InventoryRepository inventoryRepository;
    private final InventoryArtRepository artRepository;

    @Inject
    public DemoEquipmentSeeder(
            EquipmentNeedRepository needRepository,
            EventRepository eventRepository,
            InventoryRepository inventoryRepository,
            InventoryArtRepository artRepository) {
        this.needRepository = needRepository;
        this.eventRepository = eventRepository;
        this.inventoryRepository = inventoryRepository;
        this.artRepository = artRepository;
    }

    @Override
    public int order() {
        return FEDERATED_MODULES;
    }

    @Override
    public void seedStation(DemoRunContext run, DemoStationContext station) {
        int stationId = station.stationId();
        var events = eventRepository.findByStation(stationId);
        var radios = inventoryByName(stationId, "Handfunkgeräte");
        if (radios.isEmpty()) return;
        var blue = artRepository.findByName(radios.get().id(), "Funkgerät blau");
        if (blue.isEmpty()) return;

        int written = 0;
        written += needFor(events, "Übung", blue.get(), 4);
        written += needFor(events, "Kreiswettbewerb", blue.get(), 10);
        log.info("Demo: Wrote {} equipment lines for station {}", written, stationId);
    }

    private int needFor(List<StationEvent> events, String eventName, InventoryArt art, int quantity) {
        var event = events.stream()
                .filter(candidate -> eventName.equals(candidate.name()))
                .findFirst();
        if (event.isEmpty()) return 0;
        needRepository.create(event.get().id(), null, null, art.id(), null, quantity, LEAD_MINUTES, LEAD_MINUTES);
        return 1;
    }

    private Optional<Inventory> inventoryByName(int stationId, String name) {
        return inventoryRepository.findByStation(stationId).stream()
                .filter(inventory -> name.equals(inventory.name()))
                .findFirst();
    }
}
