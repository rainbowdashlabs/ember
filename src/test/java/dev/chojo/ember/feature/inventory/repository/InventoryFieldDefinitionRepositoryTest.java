/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.inventory.repository;

import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.inventory.entity.FieldConfig;
import dev.chojo.ember.feature.inventory.entity.FieldType;
import dev.chojo.ember.feature.inventory.entity.Inventory;
import dev.chojo.ember.feature.inventory.entity.InventoryFieldDefinition;
import dev.chojo.ember.feature.inventory.entity.InventoryItem;
import dev.chojo.ember.feature.inventory.entity.InventoryItemMetadata;
import dev.chojo.ember.feature.inventory.entity.InventoryType;
import dev.chojo.ember.feature.inventory.entity.ItemFieldValues;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InventoryFieldDefinitionRepositoryTest extends RepositoryTestBase {

    private static Station station;
    private static Account account;
    private static Inventory inventory;

    @BeforeAll
    static void setup() {
        account = accountRepo.create("fields@test.example", "Field", "User");
        station = stationRepo.create("FieldStation");
        inventory = inventoryRepo.create(station.id(), "Costumes", InventoryType.INTERNAL, false);
    }

    @AfterAll
    static void cleanup() {
        stationRepo.delete(station.id());
        accountRepo.delete(account.id());
    }

    @Test
    void crudAndValuePresenceProbe() {
        FieldConfig.NumberConfig numberConfig =
                new FieldConfig.NumberConfig(BigDecimal.ZERO, BigDecimal.valueOf(100), null, "kg");
        InventoryFieldDefinition created = fieldDefinitionRepo.create(
                inventory.id(), "weight", "Weight", FieldType.NUMBER, true, 10, numberConfig);
        assertEquals("weight", created.key());
        assertEquals(FieldType.NUMBER, created.fieldType());

        FieldConfig.EnumConfig enumConfig = new FieldConfig.EnumConfig(List.of(
                new FieldConfig.EnumConfig.EnumOption("a", "A"), new FieldConfig.EnumConfig.EnumOption("b", "B")));
        InventoryFieldDefinition condition = fieldDefinitionRepo.create(
                inventory.id(), "condition", "Condition", FieldType.ENUM, false, 20, enumConfig);

        List<InventoryFieldDefinition> all = fieldDefinitionRepo.findByInventory(inventory.id());
        assertEquals(2, all.size());
        assertEquals("weight", all.getFirst().key());

        assertTrue(fieldDefinitionRepo.findById(condition.id()).isPresent());
        assertTrue(fieldDefinitionRepo.findById(99999).isEmpty());

        assertTrue(fieldDefinitionRepo.update(condition.id(), "Item Condition", true, 5, enumConfig));
        InventoryFieldDefinition reloaded =
                fieldDefinitionRepo.findById(condition.id()).orElseThrow();
        assertEquals("Item Condition", reloaded.label());
        assertEquals(5, reloaded.sortOrder());
        assertTrue(reloaded.required());

        assertFalse(fieldDefinitionRepo.fieldHasAnyValue(inventory.id(), "weight"));
        LinkedHashMap<String, ItemFieldValues.FieldValue> values = new LinkedHashMap<>();
        values.put("weight", new ItemFieldValues.NumberValue(BigDecimal.valueOf(12)));
        InventoryItemMetadata metadata = new InventoryItemMetadata(false, new ItemFieldValues(values));
        InventoryItem item = inventoryRepo.createItem(inventory.id(), "WI-1", "Weighted", null, metadata);
        assertTrue(fieldDefinitionRepo.fieldHasAnyValue(inventory.id(), "weight"));
        assertFalse(fieldDefinitionRepo.fieldHasAnyValue(inventory.id(), "unset"));

        assertTrue(fieldDefinitionRepo.delete(created.id()));
        assertFalse(fieldDefinitionRepo.delete(created.id()));
        assertFalse(fieldDefinitionRepo.update(created.id(), "x", false, 0, null));

        inventoryRepo.deleteItem(item.id());
    }
}
