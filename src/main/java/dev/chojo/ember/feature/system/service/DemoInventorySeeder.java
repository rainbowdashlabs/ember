/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.system.service;

import dev.chojo.ember.feature.account.repository.AccountRepository;
import dev.chojo.ember.feature.inventory.entity.CheckResult;
import dev.chojo.ember.feature.inventory.entity.ExchangeStatus;
import dev.chojo.ember.feature.inventory.entity.FieldConfig;
import dev.chojo.ember.feature.inventory.entity.FieldType;
import dev.chojo.ember.feature.inventory.entity.Inventory;
import dev.chojo.ember.feature.inventory.entity.InventoryContainer;
import dev.chojo.ember.feature.inventory.entity.InventoryContainerKind;
import dev.chojo.ember.feature.inventory.entity.InventoryItem;
import dev.chojo.ember.feature.inventory.entity.InventoryItemMetadata;
import dev.chojo.ember.feature.inventory.entity.InventorySize;
import dev.chojo.ember.feature.inventory.entity.InventoryType;
import dev.chojo.ember.feature.inventory.entity.ItemFieldValues;
import dev.chojo.ember.feature.inventory.entity.ItemOwner;
import dev.chojo.ember.feature.inventory.repository.InventoryArtRepository;
import dev.chojo.ember.feature.inventory.repository.InventoryCheckRepository;
import dev.chojo.ember.feature.inventory.repository.InventoryRepository;
import dev.chojo.ember.feature.inventory.repository.InventoryTagRepository;
import dev.chojo.ember.feature.inventory.service.ExchangeService;
import dev.chojo.ember.feature.inventory.service.InventoryContainerService;
import dev.chojo.ember.feature.inventory.service.InventoryFieldDefinitionService;
import dev.chojo.ember.feature.inventory.service.ItemCustodyService;
import dev.chojo.ember.feature.inventory.service.ProcurementService;
import dev.chojo.ember.feature.members.entity.StationMember;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Random;
import java.util.function.Function;

/**
 * Seeder for demo inventory items, assignments, history, inventory checks, exchange requests
 * and procurements. The stages run in one step because each consumes the items the previous
 * one assigned.
 */
@Singleton
public class DemoInventorySeeder implements DemoPerStationSeeder {
    private static final Logger log = LoggerFactory.getLogger(DemoInventorySeeder.class);
    private static final List<String> EXCHANGE_REASONS = List.of(
            "Zu klein geworden",
            "Beschädigt",
            "Verschlissen",
            "Falsche Größe erhalten",
            "Verloren und brauche Ersatz",
            "Riss im Material",
            "Reißverschluss defekt");
    private static final List<ExchangeStatus> EXCHANGE_STATUSES = List.of(
            ExchangeStatus.ANNOUNCED,
            ExchangeStatus.ANNOUNCED,
            ExchangeStatus.RECEIVED,
            ExchangeStatus.ANNOUNCED,
            ExchangeStatus.RECEIVED);

    private final InventoryRepository inventoryRepository;
    private final InventoryArtRepository artRepository;
    private final InventoryTagRepository tagRepository;
    private final InventoryCheckRepository inventoryCheckRepository;
    private final AccountRepository accountRepository;
    private final InventoryContainerService containerService;
    private final InventoryFieldDefinitionService fieldDefinitionService;
    private final ExchangeService exchangeService;
    private final ProcurementService procurementService;
    private final ItemCustodyService custodyService;

    @Inject
    public DemoInventorySeeder(
            InventoryRepository inventoryRepository,
            InventoryArtRepository artRepository,
            InventoryTagRepository tagRepository,
            InventoryCheckRepository inventoryCheckRepository,
            AccountRepository accountRepository,
            InventoryContainerService containerService,
            InventoryFieldDefinitionService fieldDefinitionService,
            ExchangeService exchangeService,
            ProcurementService procurementService,
            ItemCustodyService custodyService) {
        this.inventoryRepository = inventoryRepository;
        this.artRepository = artRepository;
        this.tagRepository = tagRepository;
        this.inventoryCheckRepository = inventoryCheckRepository;
        this.accountRepository = accountRepository;
        this.containerService = containerService;
        this.fieldDefinitionService = fieldDefinitionService;
        this.exchangeService = exchangeService;
        this.procurementService = procurementService;
        this.custodyService = custodyService;
    }

    @Override
    public int order() {
        return MODULES;
    }

    @Override
    public void seedStation(DemoRunContext run, DemoStationContext station) {
        var members = station.members();
        var rng = new Random(42_002);
        seedInventory(
                station.stationId(),
                rng,
                members.anfaenger(),
                members.fortgeschritten(),
                members.groupAnfaenger().id(),
                members.groupFortgeschritten().id());
        seedInventoryChecks(
                station.stationId(), rng, members.betreuer(), members.anfaenger(), members.fortgeschritten());
        seedExchanges(station.stationId(), rng, members.anfaenger(), members.fortgeschritten(), members.betreuer());
        seedProcurements(station.stationId(), members.anfaenger(), members.fortgeschritten());
    }

    /**
     * Creates exchange requests for randomly picked assigned items, moving a portion of them to
     * the received state so the exchange list shows both stages.
     */
    private void seedExchanges(
            int stationId,
            Random rng,
            List<StationMember> anfaengerMembers,
            List<StationMember> fortgeschrittenMembers,
            List<StationMember> betreuerMembers) {
        var allKids = new ArrayList<>(anfaengerMembers);
        allKids.addAll(fortgeschrittenMembers);
        int exchangeCount = 0;
        for (var kid : allKids) {
            if (rng.nextInt(5) == 0) continue;
            var memberItems = inventoryRepository.findItemsByMember(kid.id());
            if (memberItems.isEmpty()) continue;
            var item = memberItems.get(rng.nextInt(memberItems.size()));
            var reason = EXCHANGE_REASONS.get(rng.nextInt(EXCHANGE_REASONS.size()));
            Integer newSizeId = item.sizeId();
            var sizes = item.sizeId() != null
                    ? inventoryRepository.findSizes(item.inventoryId())
                    : List.<InventorySize>of();
            int currentIdx = -1;
            for (int si = 0; si < sizes.size(); si++) {
                if (sizes.get(si).id() == item.sizeId()) {
                    currentIdx = si;
                    break;
                }
            }
            switch (reason) {
                case "Zu klein geworden" -> {
                    if (currentIdx >= 0 && currentIdx < sizes.size() - 1) {
                        newSizeId = sizes.get(currentIdx + 1).id();
                    } else {
                        reason = "Beschädigt";
                    }
                }
                case "Beschädigt",
                        "Verschlissen",
                        "Riss im Material",
                        "Reißverschluss defekt",
                        "Verloren und brauche Ersatz" -> {}
                case "Falsche Größe erhalten" -> {
                    if (sizes.size() > 1 && currentIdx >= 0) {
                        int offset = rng.nextBoolean() && currentIdx > 0 ? -1 : 1;
                        int newIdx = Math.clamp(currentIdx + offset, 0, sizes.size() - 1);
                        if (newIdx == currentIdx) {
                            newIdx = currentIdx > 0 ? currentIdx - 1 : currentIdx + 1;
                        }
                        newSizeId = sizes.get(newIdx).id();
                    }
                }
                default -> {}
            }
            var exchange = exchangeService.create(
                    stationId,
                    kid.id(),
                    "Demo User",
                    item.id(),
                    item.inventoryId(),
                    item.sizeId(),
                    newSizeId,
                    reason,
                    null);
            var targetStatus = EXCHANGE_STATUSES.get(rng.nextInt(EXCHANGE_STATUSES.size()));
            if (targetStatus != ExchangeStatus.ANNOUNCED) {
                exchangeService.updateStatus(
                        exchange.id(),
                        ExchangeStatus.RECEIVED,
                        betreuerMembers.get(rng.nextInt(betreuerMembers.size())).id(),
                        "In Bearbeitung");
            }
            exchangeCount++;
        }
        log.info("Demo: Created {} exchange requests", exchangeCount);
    }

    /**
     * Requests replacements for a lost pair of gloves and for every member still missing a sports
     * bag, so the procurement list has both single and bulk demand.
     */
    private void seedProcurements(
            int stationId, List<StationMember> anfaengerMembers, List<StationMember> fortgeschrittenMembers) {
        var inventories = inventoryRepository.findByStation(stationId);
        if (!inventories.isEmpty()) {
            var handschuheInv = inventories.stream()
                    .filter(i -> "Handschuhe".equals(i.name()))
                    .findFirst();
            if (handschuheInv.isPresent()) {
                var sizes = inventoryRepository.findSizes(handschuheInv.get().id());
                procurementService.create(
                        stationId,
                        handschuheInv.get().id(),
                        anfaengerMembers.get(2).id(),
                        sizes.isEmpty() ? null : sizes.get(2 % sizes.size()).id(),
                        "Handschuhe verloren");
            }
        }
        var sporttascheInv =
                inventories.stream().filter(i -> "Sporttasche".equals(i.name())).findFirst();
        if (sporttascheInv.isPresent()) {
            var allKids = new ArrayList<>(anfaengerMembers);
            allKids.addAll(fortgeschrittenMembers);
            for (var kid : allKids) {
                var items = inventoryRepository.findItemsByMember(kid.id());
                boolean hasSporttasche = items.stream()
                        .anyMatch(i -> i.inventoryId() == sporttascheInv.get().id());
                if (!hasSporttasche) {
                    procurementService.create(
                            stationId, sporttascheInv.get().id(), kid.id(), null, "Sporttasche fehlt");
                }
            }
        }
        log.info("Demo: Created procurements");
    }

    public void seedInventory(
            int stationId,
            Random rng,
            List<StationMember> anfaenger,
            List<StationMember> fortgeschritten,
            int anfaengerGroupId,
            int fortgeschrittenGroupId) {
        // Sizes reference from existing data
        var kleidungSizes = List.of("140", "146", "152", "158", "164", "170", "176", "182");
        var parkaSizes = List.of("XXXXS", "XXXS", "XXS", "XS", "S", "M", "L");
        var handschuhSizes = List.of("4", "5", "6", "7", "8", "9", "10");
        var stiefelSizes = List.of("34", "35", "36", "37", "38", "39", "40", "41", "42", "43");
        var tshirtSizes = List.of("128", "140", "152", "164", "176");

        // Create inventories
        var helm = inventoryRepository.create(stationId, "Helm", InventoryType.MIXED, false);

        var blouson = inventoryRepository.create(stationId, "Blouson", InventoryType.EXTERNAL, true);
        for (int i = 0; i < kleidungSizes.size(); i++)
            inventoryRepository.createSize(blouson.id(), kleidungSizes.get(i), i, "");

        var parka = inventoryRepository.create(stationId, "Parka", InventoryType.EXTERNAL, true);
        for (int i = 0; i < parkaSizes.size(); i++)
            inventoryRepository.createSize(parka.id(), parkaSizes.get(i), i, "");

        var latzhose = inventoryRepository.create(stationId, "Latzhose", InventoryType.EXTERNAL, true);
        for (int i = 0; i < kleidungSizes.size(); i++)
            inventoryRepository.createSize(latzhose.id(), kleidungSizes.get(i), i, "");

        var handschuhe = inventoryRepository.create(stationId, "Handschuhe", InventoryType.MIXED, true);
        for (int i = 0; i < handschuhSizes.size(); i++)
            inventoryRepository.createSize(handschuhe.id(), handschuhSizes.get(i), i, "");

        var stiefel = inventoryRepository.create(stationId, "Stiefel", InventoryType.INTERNAL, true);
        for (int i = 0; i < stiefelSizes.size(); i++)
            inventoryRepository.createSize(stiefel.id(), stiefelSizes.get(i), i, "");

        var sporttasche = inventoryRepository.create(stationId, "Sporttasche", InventoryType.INTERNAL, false);

        var tshirt = inventoryRepository.create(stationId, "T-Shirt", InventoryType.INTERNAL, true);
        for (int i = 0; i < tshirtSizes.size(); i++)
            inventoryRepository.createSize(tshirt.id(), tshirtSizes.get(i), i, "");

        // Gear whose owner is not on this instance: the same ownership with nobody behind it, which is what
        // makes the asserted half of the model visible. Every station keeps some, association or not.
        //
        // It is also the drawer of different things, which is why it is the one inventory here that is not
        // marked as holding one thing in many copies: several names under one heading, fetched for an
        // occasion and handed back. With it marked, the barrier is something the demo shows rather than
        // something only the tests know about, and the three features that need one thing are simply not
        // offered on it.
        var gemeindematerial =
                inventoryRepository.create(stationId, "Gemeindematerial", InventoryType.EXTERNAL, false, false);
        seedGemeindematerial(stationId, gemeindematerial.id());

        // Requirements: Anfänger and Fortgeschritten members each need 1 of each (2 T-shirts)
        for (int groupId : List.of(anfaengerGroupId, fortgeschrittenGroupId)) {
            inventoryRepository.createRequirement(helm.id(), null, groupId, null, 1);
            inventoryRepository.createRequirement(blouson.id(), null, groupId, null, 1);
            inventoryRepository.createRequirement(parka.id(), null, groupId, null, 1);
            inventoryRepository.createRequirement(latzhose.id(), null, groupId, null, 1);
            inventoryRepository.createRequirement(handschuhe.id(), null, groupId, null, 1);
            inventoryRepository.createRequirement(stiefel.id(), null, groupId, null, 1);
            inventoryRepository.createRequirement(sporttasche.id(), null, groupId, null, 1);
            inventoryRepository.createRequirement(tshirt.id(), null, groupId, null, 2);
        }

        // Create items and assign to members
        var allKids = new ArrayList<>(anfaenger);
        allKids.addAll(fortgeschritten);

        int itemCounter = 1;
        var blousonSizeList = inventoryRepository.findSizes(blouson.id());
        var parkaSizeList = inventoryRepository.findSizes(parka.id());
        var latzhoseSizeList = inventoryRepository.findSizes(latzhose.id());
        var handschuheSizeList = inventoryRepository.findSizes(handschuhe.id());
        var stiefelSizeList = inventoryRepository.findSizes(stiefel.id());
        var tshirtSizeList = inventoryRepository.findSizes(tshirt.id());

        for (var member : allKids) {
            int idx = allKids.indexOf(member);

            // Helm (mixed inventory, no size) - provided by the station itself
            var helmItem = inventoryRepository.createItem(
                    helm.id(),
                    "H-" + String.format("%03d", itemCounter++),
                    "Helm",
                    null,
                    null,
                    ItemOwner.STATION,
                    null);
            custodyService.assignToMember(helmItem.id(), member.id(), "");

            // Blouson (provided by the body above the station)
            var blousonItem = inventoryRepository.createItem(
                    blouson.id(),
                    "BL-" + String.format("%03d", itemCounter++),
                    "Blouson",
                    blousonSizeList.get(idx % blousonSizeList.size()).id(),
                    null,
                    ItemOwner.CLUSTER,
                    null);
            custodyService.assignToMember(blousonItem.id(), member.id(), "");

            // Parka (provided by the body above the station)
            var parkaItem = inventoryRepository.createItem(
                    parka.id(),
                    "PA-" + String.format("%03d", itemCounter++),
                    "Parka",
                    parkaSizeList.get(idx % parkaSizeList.size()).id(),
                    null,
                    ItemOwner.CLUSTER,
                    null);
            custodyService.assignToMember(parkaItem.id(), member.id(), "");

            // Latzhose (provided by the body above the station)
            var latzItem = inventoryRepository.createItem(
                    latzhose.id(),
                    "LH-" + String.format("%03d", itemCounter++),
                    "Latzhose",
                    latzhoseSizeList.get(idx % latzhoseSizeList.size()).id(),
                    null,
                    ItemOwner.CLUSTER,
                    null);
            custodyService.assignToMember(latzItem.id(), member.id(), "");

            // Handschuhe (mixed inventory) - provided by the station itself
            var handschuhItem = inventoryRepository.createItem(
                    handschuhe.id(),
                    "HS-" + String.format("%03d", itemCounter++),
                    "Handschuhe",
                    handschuheSizeList.get(idx % handschuheSizeList.size()).id(),
                    null,
                    ItemOwner.STATION,
                    null);
            custodyService.assignToMember(handschuhItem.id(), member.id(), "");

            // Stiefel (station-owned)
            var stiefelItem = inventoryRepository.createItem(
                    stiefel.id(),
                    "ST-" + String.format("%03d", itemCounter++),
                    "Stiefel",
                    stiefelSizeList.get(idx % stiefelSizeList.size()).id(),
                    null,
                    ItemOwner.STATION,
                    null);
            custodyService.assignToMember(stiefelItem.id(), member.id(), "");

            // T-Shirt (station-owned, 2 per member)
            for (int t = 0; t < 2; t++) {
                var tshirtItem = inventoryRepository.createItem(
                        tshirt.id(),
                        "TS-" + String.format("%03d", itemCounter++),
                        "T-Shirt",
                        tshirtSizeList.get(idx % tshirtSizeList.size()).id(),
                        null,
                        ItemOwner.STATION,
                        null);
                custodyService.assignToMember(tshirtItem.id(), member.id(), "");
            }

            // Sporttasche (station-owned, ~70% get one, rest need procurement)
            if (rng.nextInt(10) < 7) {
                var tasche = inventoryRepository.createItem(
                        sporttasche.id(),
                        "SP-" + String.format("%03d", itemCounter++),
                        "Sporttasche",
                        null,
                        null,
                        ItemOwner.STATION,
                        null);
                custodyService.assignToMember(tasche.id(), member.id(), "");
            }
        }

        // Add some unassigned spare items, all station-owned
        for (int i = 0; i < 5; i++) {
            inventoryRepository.createItem(
                    helm.id(),
                    "H-" + String.format("%03d", itemCounter++),
                    "Helm Ersatz",
                    null,
                    null,
                    ItemOwner.STATION,
                    null);
        }
        for (int i = 0; i < 3; i++) {
            inventoryRepository.createItem(
                    sporttasche.id(),
                    "SP-" + String.format("%03d", itemCounter++),
                    "Sporttasche Ersatz",
                    null,
                    null,
                    ItemOwner.STATION,
                    null);
        }

        // One glove per size provided by the body above the station, in the same mixed inventory
        var handschuhSizeListOwned = inventoryRepository.findSizes(handschuhe.id());
        for (var size : handschuhSizeListOwned) {
            var kid = allKids.get(rng.nextInt(allKids.size()));
            var ownedGlove = inventoryRepository.createItem(
                    handschuhe.id(),
                    "HS-" + String.format("%03d", itemCounter++),
                    "Handschuhe (Kreisverband) " + size.label(),
                    size.id(),
                    null,
                    ItemOwner.CLUSTER,
                    null);
            custodyService.assignToMember(ownedGlove.id(), kid.id(), "");
        }

        // Generate item assignment history for internal items
        // For ~40% of items, create a history of 1-3 previous owners
        var internalInventoryIds = List.of(helm.id(), stiefel.id(), sporttasche.id(), handschuhe.id());
        var allInternalItems = new ArrayList<InventoryItem>();
        for (int invId : internalInventoryIds) {
            allInternalItems.addAll(inventoryRepository.findItems(invId));
        }

        int historyCount = 0;
        for (var item : allInternalItems) {
            if (item.assignedTo() == null) continue;
            if (rng.nextInt(10) < 6) continue; // skip 60%

            int prevOwnerCount = 1 + rng.nextInt(3);
            Instant cursor = Instant.now().minus(Duration.ofDays(365 + rng.nextInt(730)));

            for (int h = 0; h < prevOwnerCount; h++) {
                var prevOwner = allKids.get(rng.nextInt(allKids.size()));
                var prevAccount =
                        accountRepository.findById(prevOwner.accountId()).orElse(null);
                String prevName = prevAccount != null
                        ? (prevAccount.firstName() + " " + prevAccount.lastName()).trim()
                        : "#" + prevOwner.id();

                Instant givenOut = cursor;
                cursor = cursor.plus(Duration.ofDays(30 + rng.nextInt(180)));
                Instant returned = cursor;
                cursor = cursor.plus(Duration.ofDays(1 + rng.nextInt(14)));

                inventoryRepository.createHistoryWithDates(item.id(), prevOwner.id(), prevName, givenOut, returned);
                historyCount++;
            }

            // Current owner - given out after last return, no return date
            var currentAccount = accountRepository
                    .findById(allKids.stream()
                            .filter(m -> m.id() == item.assignedTo())
                            .findFirst()
                            .map(StationMember::accountId)
                            .orElse(0))
                    .orElse(null);
            String currentName = currentAccount != null
                    ? (currentAccount.firstName() + " " + currentAccount.lastName()).trim()
                    : "#" + item.assignedTo();
            inventoryRepository.createHistoryWithDates(item.id(), item.assignedTo(), currentName, cursor, null);
            historyCount++;
        }

        seedStorageContainers(stationId, rng, helm, stiefel, sporttasche, blouson, parka, latzhose);
        seedCustomFields(stiefel.id(), helm.id());
        seedMixedDrawers(stationId);

        log.info("Demo: Created {} inventory items with {} history entries", itemCounter - 1, historyCount);
    }

    /**
     * The everyday drawers a station keeps beside its uniform shelves.
     *
     * <p>Games and odds and ends hold pieces nobody would ever count by kind, so every piece stands
     * under its own name and none of them carries one. The radio drawer is the mixed case: six of
     * the blue kind, whose pieces need no names of their own, and a case nobody gave a kind to,
     * which is the ordinary state of most pieces and the state every screen has to read without
     * complaining.
     */
    private void seedMixedDrawers(int stationId) {
        var spiele = inventoryRepository.create(stationId, "Spiele", InventoryType.INTERNAL, false);
        inventoryRepository.createItem(spiele.id(), null, "Die Siedler von Catan", null, null);
        inventoryRepository.createItem(spiele.id(), null, "Uno", null, null);
        inventoryRepository.createItem(spiele.id(), null, "Twister", null, null);

        var sonstiges = inventoryRepository.create(stationId, "Sonstiges", InventoryType.INTERNAL, false);
        inventoryRepository.createItem(sonstiges.id(), null, "Laminiergerät", null, null);
        inventoryRepository.createItem(sonstiges.id(), null, "Playmobil-Feuerwehrauto", null, null);
        inventoryRepository.createItem(sonstiges.id(), null, "Ladestation", null, null);
        inventoryRepository.createItem(sonstiges.id(), null, "Antenne", null, null);

        var funk = inventoryRepository.create(stationId, "Handfunkgeräte", InventoryType.INTERNAL, false, false);
        var blau = artRepository.create(funk.id(), "Funkgerät blau", "Kanal 1 bis 4", 10);
        for (int i = 1; i <= 6; i++) {
            inventoryRepository.createItem(
                    funk.id(), "FUNK-B%02d".formatted(i), "Funkgerät blau", null, blau.id(), null, null, null);
        }
        inventoryRepository.createItem(funk.id(), "FUNK-K01", "Koffer", null, null);

        log.info("Demo: Created 3 further inventories in station {}", stationId);
    }

    private void seedStorageContainers(
            int stationId,
            Random rng,
            Inventory helm,
            Inventory stiefel,
            Inventory sporttasche,
            Inventory blouson,
            Inventory parka,
            Inventory latzhose) {
        containerService.seedDefaultKinds(stationId);

        var kinds = containerService.listKinds(stationId);
        Function<String, Integer> kindIdOf = key -> kinds.stream()
                .filter(k -> k.key().equals(key))
                .findFirst()
                .map(InventoryContainerKind::id)
                .orElse(null);

        InventoryContainer geraeteraum = containerService.create(
                stationId,
                null,
                "GR-1",
                "Geräteraum",
                kindIdOf.apply("equipment_room"),
                "Hauptgeräteraum der Wache",
                null);
        InventoryContainer helmregal = containerService.create(
                stationId,
                geraeteraum.id(),
                "GR-1-H",
                "Helmregal",
                kindIdOf.apply("shelf"),
                "Einsatzhelme & Kopfschutz",
                null);
        InventoryContainer stiefelregal = containerService.create(
                stationId,
                geraeteraum.id(),
                "GR-1-S",
                "Stiefelregal",
                kindIdOf.apply("shelf"),
                "Einsatzstiefel der Mannschaft",
                null);
        InventoryContainer helmkisteEinsatz = containerService.create(
                stationId,
                helmregal.id(),
                "GR-1-H-K1",
                "Helmkiste Einsatz",
                kindIdOf.apply("gear_box"),
                "Tagesbestand",
                null);
        InventoryContainer helmkisteReserve = containerService.create(
                stationId,
                helmregal.id(),
                "GR-1-H-K2",
                "Helmkiste Reserve",
                kindIdOf.apply("gear_box"),
                "Reservebestand",
                null);
        InventoryContainer stiefelkiste = containerService.create(
                stationId,
                stiefelregal.id(),
                "GR-1-S-K1",
                "Stiefelkiste",
                kindIdOf.apply("gear_box"),
                "Einsatzstiefel",
                null);

        InventoryContainer umkleide = containerService.create(
                stationId,
                null,
                "UM-1",
                "Umkleide Mannschaft",
                kindIdOf.apply("equipment_room"),
                "Einsatzumkleide",
                null);
        InventoryContainer spind = containerService.create(
                stationId, umkleide.id(), "UM-1-SP", "Spind 1", kindIdOf.apply("drawer"), "Einsatzbekleidung", null);
        InventoryContainer jackenkiste = containerService.create(
                stationId,
                spind.id(),
                "UM-1-SP-K1",
                "Jackenkiste",
                kindIdOf.apply("gear_box"),
                "Einsatzjacken & Wetterschutz",
                null);

        placeItemsInto(helm.id(), helmkisteEinsatz.id(), 6);
        placeItemsInto(helm.id(), helmkisteReserve.id(), 3);
        placeItemsInto(stiefel.id(), stiefelkiste.id(), 8);
        placeItemsInto(sporttasche.id(), umkleide.id(), 4);
        placeItemsInto(blouson.id(), jackenkiste.id(), 5);
        placeItemsInto(parka.id(), jackenkiste.id(), 4);
        placeItemsInto(latzhose.id(), spind.id(), 6);
    }

    private void placeItemsInto(int inventoryId, int containerId, int count) {
        var items = inventoryRepository.findItems(inventoryId);
        int placed = 0;
        for (var item : items) {
            if (placed >= count) break;
            if (item.containerId() != null) continue;
            custodyService.placeInContainer(item.id(), containerId);
            placed++;
        }
    }

    /**
     * The mixed drawer, with kinds in it.
     *
     * <p>The one inventory here that holds different things, so the one that can have kinds at all.
     * It is written the way a real drawer looks: two kinds with several pieces each, whose pieces
     * need no names of their own, and four one-offs with no kind, which is the ordinary state of
     * most pieces and the state every screen has to read without complaining. A field hangs on one
     * of the kinds rather than on the inventory, because a call sign is nonsense on a coffee machine
     * standing in the same cupboard.
     */
    private void seedGemeindematerial(int stationId, int inventoryId) {
        var blau = artRepository.create(inventoryId, "Funkgerät blau", "Kanal 1 bis 4", 10);
        var gruen = artRepository.create(inventoryId, "Funkgerät grün", "Kanal 5 bis 8", 20);
        var funk = tagRepository.create(stationId, "Funk", "#3694FF");
        var gemeinde = tagRepository.create(stationId, "Gemeinde", null);
        var radios = new ArrayList<InventoryItem>();
        for (int i = 1; i <= 3; i++) {
            radios.add(inventoryRepository.createItem(
                    inventoryId,
                    "GM-B%02d".formatted(i),
                    "Funkgerät blau",
                    null,
                    blau.id(),
                    null,
                    ItemOwner.CLUSTER,
                    null));
        }
        for (int i = 1; i <= 2; i++) {
            radios.add(inventoryRepository.createItem(
                    inventoryId,
                    "GM-G%02d".formatted(i),
                    "Funkgerät grün",
                    null,
                    gruen.id(),
                    null,
                    ItemOwner.CLUSTER,
                    null));
        }
        fieldDefinitionService.create(
                inventoryId,
                blau.id(),
                null,
                "call_sign",
                "Rufname",
                FieldType.TEXT,
                false,
                10,
                fieldDefinitionService.defaultConfig(FieldType.TEXT));

        var ladestation = inventoryRepository.createItem(
                inventoryId, "GM-0001", "Ladestation", null, null, ItemOwner.CLUSTER, null);
        inventoryRepository.createItem(inventoryId, "GM-0002", "Laminiergerät", null, null, ItemOwner.CLUSTER, null);
        var beamer = inventoryRepository.createItem(
                inventoryId, "GM-0003", "Beamer der Gemeinde", null, null, ItemOwner.CLUSTER, null);
        inventoryRepository.createItem(inventoryId, "GM-0004", "Kaffeemaschine", null, null, ItemOwner.CLUSTER, null);

        for (var radio : radios) {
            tagRepository.setItemTags(radio.id(), stationId, List.of(funk.id(), gemeinde.id()));
        }
        tagRepository.setItemTags(ladestation.id(), stationId, List.of(funk.id(), gemeinde.id()));
        tagRepository.setItemTags(beamer.id(), stationId, List.of(gemeinde.id()));
    }

    private void seedCustomFields(int stiefelId, int helmId) {
        FieldConfig.EnumConfig condition = new FieldConfig.EnumConfig(List.of(
                new FieldConfig.EnumConfig.EnumOption("new", "Neu"),
                new FieldConfig.EnumConfig.EnumOption("good", "Gut"),
                new FieldConfig.EnumConfig.EnumOption("worn", "Abgenutzt")));
        fieldDefinitionService.create(stiefelId, "last_service", "Letzter Service", FieldType.DATE, false, 10, null);
        fieldDefinitionService.create(stiefelId, "condition", "Zustand", FieldType.ENUM, false, 20, condition);
        fieldDefinitionService.create(stiefelId, "notes", "Notizen", FieldType.TEXT, false, 30, null);
        fieldDefinitionService.create(
                stiefelId,
                "weight_kg",
                "Gewicht",
                FieldType.NUMBER,
                false,
                40,
                new FieldConfig.NumberConfig(BigDecimal.ZERO, BigDecimal.valueOf(50), BigDecimal.valueOf(0.1), "kg"));
        fieldDefinitionService.create(
                stiefelId,
                "owner_supplied",
                "Eigener Stiefel",
                FieldType.BOOLEAN,
                false,
                50,
                new FieldConfig.BooleanConfig("Ja", "Nein"));

        fieldDefinitionService.create(
                helmId,
                "size_cm",
                "Kopfumfang",
                FieldType.NUMBER,
                false,
                10,
                new FieldConfig.NumberConfig(BigDecimal.valueOf(50), BigDecimal.valueOf(65), BigDecimal.ONE, "cm"));

        var stiefelItems = inventoryRepository.findItems(stiefelId);
        int idx = 0;
        var conditions = List.of("new", "good", "worn");
        for (var item : stiefelItems) {
            LinkedHashMap<String, ItemFieldValues.FieldValue> values = new LinkedHashMap<>();
            values.put("condition", new ItemFieldValues.EnumValue(conditions.get(idx % conditions.size())));
            values.put(
                    "last_service",
                    new ItemFieldValues.DateValue(LocalDate.now().minusDays(30L * (idx % 12))));
            values.put(
                    "weight_kg",
                    new ItemFieldValues.NumberValue(
                            BigDecimal.valueOf(1.2 + 0.1 * (idx % 5)).setScale(1, RoundingMode.HALF_UP)));
            if (idx % 3 == 0) {
                values.put("notes", new ItemFieldValues.TextValue("Aus letzter Inventur"));
            }
            inventoryRepository.updateItem(
                    item.id(),
                    item.internalId(),
                    item.name(),
                    item.sizeId(),
                    item.artId(),
                    new InventoryItemMetadata(new ItemFieldValues(values)));
            idx++;
        }
        log.info("Demo: Seeded custom fields and storage containers for inventories {} and {}", stiefelId, helmId);
    }

    public void seedInventoryChecks(
            int stationId,
            Random rng,
            List<StationMember> betreuer,
            List<StationMember> anfaenger,
            List<StationMember> fortgeschritten) {
        var allKids = new ArrayList<>(anfaenger);
        allKids.addAll(fortgeschritten);

        // Some Betreuer checked some kids
        int checkedCount = 0;
        for (StationMember allKid : allKids) {
            if (rng.nextInt(3) != 0) continue; // ~1/3 of kids have been checked
            var checker = betreuer.get(rng.nextInt(betreuer.size()));
            var check = inventoryCheckRepository.createCheck(stationId, allKid.id(), checker.id());

            // Check all items assigned to this kid
            var items = inventoryRepository.findItemsByMember(allKid.id());
            for (var item : items) {
                CheckResult result;
                int roll = rng.nextInt(20);
                if (roll == 0) {
                    result = CheckResult.LOST;
                } else if (roll < 3) {
                    result = CheckResult.NOT_IN_POSSESSION;
                } else {
                    result = CheckResult.CONFIRMED;
                }
                String note = result == CheckResult.LOST ? "Seit letzter Übung vermisst" : "";
                inventoryCheckRepository.createCheckItem(check.id(), item.id(), item.inventoryId(), result, note);
                if (result == CheckResult.LOST) {
                    custodyService.markLost(item.id(), note, null);
                }
            }
            checkedCount++;
        }
        log.info("Demo: Created inventory checks for {} members", checkedCount);
    }
}
