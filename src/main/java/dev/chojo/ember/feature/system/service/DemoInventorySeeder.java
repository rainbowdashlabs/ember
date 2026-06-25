/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.system.service;

import dev.chojo.ember.feature.account.repository.AccountRepository;
import dev.chojo.ember.feature.inventory.entity.CheckResult;
import dev.chojo.ember.feature.inventory.entity.FieldConfig;
import dev.chojo.ember.feature.inventory.entity.FieldType;
import dev.chojo.ember.feature.inventory.entity.InventoryContainer;
import dev.chojo.ember.feature.inventory.entity.InventoryContainerKind;
import dev.chojo.ember.feature.inventory.entity.InventoryItem;
import dev.chojo.ember.feature.inventory.entity.InventoryItemMetadata;
import dev.chojo.ember.feature.inventory.entity.InventoryType;
import dev.chojo.ember.feature.inventory.entity.ItemFieldValues;
import dev.chojo.ember.feature.inventory.repository.InventoryCheckRepository;
import dev.chojo.ember.feature.inventory.repository.InventoryRepository;
import dev.chojo.ember.feature.inventory.service.InventoryContainerService;
import dev.chojo.ember.feature.inventory.service.InventoryFieldDefinitionService;
import dev.chojo.ember.feature.members.entity.StationMember;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Random;

/**
 * Seeder for demo inventory items, assignments, history, and inventory checks.
 */
@Singleton
public class DemoInventorySeeder {
    private static final Logger log = LoggerFactory.getLogger(DemoInventorySeeder.class);

    private final InventoryRepository inventoryRepository;
    private final InventoryCheckRepository inventoryCheckRepository;
    private final AccountRepository accountRepository;
    private final InventoryContainerService containerService;
    private final InventoryFieldDefinitionService fieldDefinitionService;

    @Inject
    public DemoInventorySeeder(
            InventoryRepository inventoryRepository,
            InventoryCheckRepository inventoryCheckRepository,
            AccountRepository accountRepository,
            InventoryContainerService containerService,
            InventoryFieldDefinitionService fieldDefinitionService) {
        this.inventoryRepository = inventoryRepository;
        this.inventoryCheckRepository = inventoryCheckRepository;
        this.accountRepository = accountRepository;
        this.containerService = containerService;
        this.fieldDefinitionService = fieldDefinitionService;
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

        // Requirements: Anfänger and Fortgeschritten members each need 1 of each (2 T-shirts)
        for (int groupId : List.of(anfaengerGroupId, fortgeschrittenGroupId)) {
            inventoryRepository.createRequirement(helm.id(), null, groupId, 1);
            inventoryRepository.createRequirement(blouson.id(), null, groupId, 1);
            inventoryRepository.createRequirement(parka.id(), null, groupId, 1);
            inventoryRepository.createRequirement(latzhose.id(), null, groupId, 1);
            inventoryRepository.createRequirement(handschuhe.id(), null, groupId, 1);
            inventoryRepository.createRequirement(stiefel.id(), null, groupId, 1);
            inventoryRepository.createRequirement(sporttasche.id(), null, groupId, 1);
            inventoryRepository.createRequirement(tshirt.id(), null, groupId, 2);
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

            // Helm (MIXED, no size) — station-provided = INTERNAL
            var helmItem = inventoryRepository.createItem(
                    helm.id(),
                    "H-" + String.format("%03d", itemCounter++),
                    "Helm",
                    null,
                    null,
                    InventoryItem.ItemSource.INTERNAL);
            inventoryRepository.assignItem(helmItem.id(), member.id());

            // Blouson (EXTERNAL)
            var blousonItem = inventoryRepository.createItem(
                    blouson.id(),
                    "BL-" + String.format("%03d", itemCounter++),
                    "Blouson",
                    blousonSizeList.get(idx % blousonSizeList.size()).id(),
                    null,
                    InventoryItem.ItemSource.EXTERNAL);
            inventoryRepository.assignItem(blousonItem.id(), member.id());

            // Parka (EXTERNAL)
            var parkaItem = inventoryRepository.createItem(
                    parka.id(),
                    "PA-" + String.format("%03d", itemCounter++),
                    "Parka",
                    parkaSizeList.get(idx % parkaSizeList.size()).id(),
                    null,
                    InventoryItem.ItemSource.EXTERNAL);
            inventoryRepository.assignItem(parkaItem.id(), member.id());

            // Latzhose (EXTERNAL)
            var latzItem = inventoryRepository.createItem(
                    latzhose.id(),
                    "LH-" + String.format("%03d", itemCounter++),
                    "Latzhose",
                    latzhoseSizeList.get(idx % latzhoseSizeList.size()).id(),
                    null,
                    InventoryItem.ItemSource.EXTERNAL);
            inventoryRepository.assignItem(latzItem.id(), member.id());

            // Handschuhe (MIXED) — station-provided = INTERNAL
            var handschuhItem = inventoryRepository.createItem(
                    handschuhe.id(),
                    "HS-" + String.format("%03d", itemCounter++),
                    "Handschuhe",
                    handschuheSizeList.get(idx % handschuheSizeList.size()).id(),
                    null,
                    InventoryItem.ItemSource.INTERNAL);
            inventoryRepository.assignItem(handschuhItem.id(), member.id());

            // Stiefel (INTERNAL)
            var stiefelItem = inventoryRepository.createItem(
                    stiefel.id(),
                    "ST-" + String.format("%03d", itemCounter++),
                    "Stiefel",
                    stiefelSizeList.get(idx % stiefelSizeList.size()).id(),
                    null,
                    InventoryItem.ItemSource.INTERNAL);
            inventoryRepository.assignItem(stiefelItem.id(), member.id());

            // T-Shirt (INTERNAL, 2 per member)
            for (int t = 0; t < 2; t++) {
                var tshirtItem = inventoryRepository.createItem(
                        tshirt.id(),
                        "TS-" + String.format("%03d", itemCounter++),
                        "T-Shirt",
                        tshirtSizeList.get(idx % tshirtSizeList.size()).id(),
                        null,
                        InventoryItem.ItemSource.INTERNAL);
                inventoryRepository.assignItem(tshirtItem.id(), member.id());
            }

            // Sporttasche (INTERNAL, ~70% get one, rest need procurement)
            if (rng.nextInt(10) < 7) {
                var tasche = inventoryRepository.createItem(
                        sporttasche.id(),
                        "SP-" + String.format("%03d", itemCounter++),
                        "Sporttasche",
                        null,
                        null,
                        InventoryItem.ItemSource.INTERNAL);
                inventoryRepository.assignItem(tasche.id(), member.id());
            }
        }

        // Add some unassigned spare items (INTERNAL)
        for (int i = 0; i < 5; i++) {
            inventoryRepository.createItem(
                    helm.id(),
                    "H-" + String.format("%03d", itemCounter++),
                    "Helm Ersatz",
                    null,
                    null,
                    InventoryItem.ItemSource.INTERNAL);
        }
        for (int i = 0; i < 3; i++) {
            inventoryRepository.createItem(
                    sporttasche.id(),
                    "SP-" + String.format("%03d", itemCounter++),
                    "Sporttasche Ersatz",
                    null,
                    null,
                    InventoryItem.ItemSource.INTERNAL);
        }

        // Add one personally owned Handschuh per size (MIXED → EXTERNAL = personally owned)
        var handschuhSizeListOwned = inventoryRepository.findSizes(handschuhe.id());
        for (var size : handschuhSizeListOwned) {
            var kid = allKids.get(rng.nextInt(allKids.size()));
            var ownedGlove = inventoryRepository.createItem(
                    handschuhe.id(),
                    "HS-" + String.format("%03d", itemCounter++),
                    "Handschuhe (eigen) " + size.label(),
                    size.id(),
                    new InventoryItemMetadata(true),
                    InventoryItem.ItemSource.EXTERNAL);
            inventoryRepository.assignItem(ownedGlove.id(), kid.id());
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

            // Current owner — given out after last return, no return date
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

        log.info("Demo: Created {} inventory items with {} history entries", itemCounter - 1, historyCount);
    }

    private void seedStorageContainers(
            int stationId,
            Random rng,
            dev.chojo.ember.feature.inventory.entity.Inventory helm,
            dev.chojo.ember.feature.inventory.entity.Inventory stiefel,
            dev.chojo.ember.feature.inventory.entity.Inventory sporttasche,
            dev.chojo.ember.feature.inventory.entity.Inventory blouson,
            dev.chojo.ember.feature.inventory.entity.Inventory parka,
            dev.chojo.ember.feature.inventory.entity.Inventory latzhose) {
        containerService.seedDefaultKinds(stationId);
        containerService.createKind(stationId, "tour_case", "Tour Case", "suitcase", 80, true);
        containerService.createKind(stationId, "workshop_bench", "Werkbank", "warehouse", 90, true);

        var kinds = containerService.listKinds(stationId);
        java.util.function.Function<String, Integer> kindIdOf = key -> kinds.stream()
                .filter(k -> k.key().equals(key))
                .findFirst()
                .map(InventoryContainerKind::id)
                .orElse(null);

        InventoryContainer lagerA = containerService.create(
                stationId, null, "STO-A", "Lager A", kindIdOf.apply("room"), "Hauptlager", null);
        InventoryContainer regal1 = containerService.create(
                stationId, lagerA.id(), "STO-A-R1", "Regal 1", kindIdOf.apply("shelf"), "Helme & Kopfschutz", null);
        InventoryContainer regal2 = containerService.create(
                stationId, lagerA.id(), "STO-A-R2", "Regal 2", kindIdOf.apply("shelf"), "Stiefel & Schuhe", null);
        InventoryContainer kiste1 = containerService.create(
                stationId, regal1.id(), "STO-A-R1-B1", "Kiste 1", kindIdOf.apply("box"), "Helme", null);
        InventoryContainer kiste2 = containerService.create(
                stationId, regal1.id(), "STO-A-R1-B2", "Kiste 2", kindIdOf.apply("box"), "Helme Ersatz", null);
        InventoryContainer kiste3 = containerService.create(
                stationId, regal2.id(), "STO-A-R2-B1", "Kiste 3", kindIdOf.apply("box"), "Stiefel", null);

        InventoryContainer backstage = containerService.create(
                stationId, null, "STO-B", "Backstage", kindIdOf.apply("area"), "Hinter der Bühne", null);
        InventoryContainer schrank = containerService.create(
                stationId, backstage.id(), "STO-B-S1", "Schrank", kindIdOf.apply("drawer"), "Kleidung", null);
        InventoryContainer kiste4 = containerService.create(
                stationId, schrank.id(), "STO-B-S1-B1", "Kiste 4", kindIdOf.apply("box"), "Blousons & Parkas", null);

        placeItemsInto(helm.id(), kiste1.id(), 6);
        placeItemsInto(helm.id(), kiste2.id(), 3);
        placeItemsInto(stiefel.id(), kiste3.id(), 8);
        placeItemsInto(sporttasche.id(), backstage.id(), 4);
        placeItemsInto(blouson.id(), kiste4.id(), 5);
        placeItemsInto(parka.id(), kiste4.id(), 4);
        placeItemsInto(latzhose.id(), schrank.id(), 6);
    }

    private void placeItemsInto(int inventoryId, int containerId, int count) {
        var items = inventoryRepository.findItems(inventoryId);
        int placed = 0;
        for (var item : items) {
            if (placed >= count) break;
            if (item.containerId() != null) continue;
            inventoryRepository.setItemContainer(item.id(), containerId);
            placed++;
        }
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
                            BigDecimal.valueOf(1.2 + 0.1 * (idx % 5)).setScale(1, java.math.RoundingMode.HALF_UP)));
            if (idx % 3 == 0) {
                values.put("notes", new ItemFieldValues.TextValue("Aus letzter Inventur"));
            }
            inventoryRepository.updateItem(
                    item.id(),
                    item.internalId(),
                    item.name(),
                    item.sizeId(),
                    new InventoryItemMetadata(false, new ItemFieldValues(values)));
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
                    inventoryRepository.markLost(item.id());
                }
            }
            checkedCount++;
        }
        log.info("Demo: Created inventory checks for {} members", checkedCount);
    }
}
