/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.inventory.service;

import dev.chojo.ember.feature.inventory.entity.FieldConfig;
import dev.chojo.ember.feature.inventory.entity.FieldType;
import dev.chojo.ember.feature.inventory.entity.InventoryFieldDefinition;
import dev.chojo.ember.feature.inventory.repository.InventoryFieldDefinitionRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InventoryFieldDefinitionServiceTest {

    private final InventoryFieldDefinitionRepository repository =
            Mockito.mock(InventoryFieldDefinitionRepository.class);
    private final InventoryFieldDefinitionService service = new InventoryFieldDefinitionService(repository);

    private static InventoryFieldDefinition definition(int inventoryId, String key, FieldType type, FieldConfig cfg) {
        return new InventoryFieldDefinition(7, inventoryId, null, null, key, "Label", type, false, 0, cfg);
    }

    @Test
    void createValidatesAndDelegates() {
        Mockito.when(repository.findInventoryLevel(1)).thenReturn(List.of());
        Mockito.when(repository.create(
                        Mockito.eq(1),
                        Mockito.isNull(),
                        Mockito.isNull(),
                        Mockito.eq("weight"),
                        Mockito.eq("Weight"),
                        Mockito.eq(FieldType.NUMBER),
                        Mockito.eq(false),
                        Mockito.eq(0),
                        Mockito.any(FieldConfig.class)))
                .thenAnswer(inv -> definition(1, "weight", FieldType.NUMBER, service.defaultConfig(FieldType.NUMBER)));
        InventoryFieldDefinition created = service.create(1, "weight", "Weight", FieldType.NUMBER, false, 0, null);
        assertEquals("weight", created.key());

        ArgumentCaptor<FieldConfig> configCaptor = ArgumentCaptor.forClass(FieldConfig.class);
        Mockito.verify(repository)
                .create(
                        Mockito.eq(1),
                        Mockito.isNull(),
                        Mockito.isNull(),
                        Mockito.eq("weight"),
                        Mockito.eq("Weight"),
                        Mockito.eq(FieldType.NUMBER),
                        Mockito.eq(false),
                        Mockito.eq(0),
                        configCaptor.capture());
        assertEquals(service.defaultConfig(FieldType.NUMBER), configCaptor.getValue());
    }

    @Test
    void createRefusesBothLevelsAtOnce() {
        assertThrows(
                IllegalArgumentException.class,
                () -> service.create(1, 2, 3, "key", "Lbl", FieldType.TEXT, false, 0, null));
    }

    @Test
    void createRejectsInvalidKey() {
        assertThrows(
                IllegalArgumentException.class,
                () -> service.create(1, "Bad Key", "X", FieldType.TEXT, false, 0, null));
        assertThrows(
                IllegalArgumentException.class, () -> service.create(1, null, "X", FieldType.TEXT, false, 0, null));
    }

    @Test
    void createRejectsBlankLabel() {
        assertThrows(
                IllegalArgumentException.class, () -> service.create(1, "key", "  ", FieldType.TEXT, false, 0, null));
    }

    @Test
    void createRejectsMissingType() {
        assertThrows(IllegalArgumentException.class, () -> service.create(1, "key", "Lbl", null, false, 0, null));
    }

    @Test
    void createRejectsConfigTypeMismatch() {
        FieldConfig.TextConfig text = new FieldConfig.TextConfig(false, 10);
        assertThrows(
                IllegalArgumentException.class,
                () -> service.create(1, "key", "Lbl", FieldType.NUMBER, false, 0, text));
    }

    @Test
    void createRejectsDuplicateKey() {
        Mockito.when(repository.findInventoryLevel(1))
                .thenReturn(
                        List.of(definition(1, "weight", FieldType.NUMBER, service.defaultConfig(FieldType.NUMBER))));
        assertThrows(
                IllegalArgumentException.class,
                () -> service.create(1, "weight", "Weight", FieldType.NUMBER, false, 0, null));
    }

    @Test
    void updatePathsAndGuards() {
        InventoryFieldDefinition existing =
                definition(1, "weight", FieldType.NUMBER, service.defaultConfig(FieldType.NUMBER));
        Mockito.when(repository.findById(7)).thenReturn(Optional.of(existing));
        Mockito.when(repository.update(
                        Mockito.eq(7),
                        Mockito.anyString(),
                        Mockito.anyBoolean(),
                        Mockito.anyInt(),
                        Mockito.any(FieldConfig.class)))
                .thenReturn(true);

        Optional<InventoryFieldDefinition> updated = service.update(7, "Weight Label", true, 5, null);
        assertTrue(updated.isPresent());
        assertSame(existing, updated.get());

        assertThrows(IllegalArgumentException.class, () -> service.update(7, "  ", true, 0, null));
        assertThrows(
                IllegalArgumentException.class,
                () -> service.update(7, "Label", true, 0, new FieldConfig.TextConfig(false, 10)));

        Mockito.when(repository.findById(99)).thenReturn(Optional.empty());
        assertTrue(service.update(99, "Label", true, 0, null).isEmpty());

        Mockito.when(repository.update(
                        Mockito.eq(7),
                        Mockito.anyString(),
                        Mockito.anyBoolean(),
                        Mockito.anyInt(),
                        Mockito.any(FieldConfig.class)))
                .thenReturn(false);
        assertTrue(service.update(7, "Other", false, 0, null).isEmpty());
    }

    @Test
    void deleteAndPresenceProbeDelegate() {
        Mockito.when(repository.delete(7)).thenReturn(true);
        assertTrue(service.delete(7));
        Mockito.when(repository.fieldHasAnyValue(1, "weight")).thenReturn(true);
        assertTrue(service.fieldHasAnyValue(1, "weight"));
    }

    @Test
    void findByInventoryAndFindByIdDelegate() {
        InventoryFieldDefinition definition =
                definition(1, "weight", FieldType.NUMBER, service.defaultConfig(FieldType.NUMBER));
        Mockito.when(repository.findByInventory(1)).thenReturn(List.of(definition));
        Mockito.when(repository.findById(7)).thenReturn(Optional.of(definition));
        assertEquals(1, service.findByInventory(1).size());
        assertTrue(service.findById(7).isPresent());
    }

    @Test
    void defaultConfigsCoverEveryType() {
        for (FieldType type : FieldType.values()) {
            FieldConfig cfg = service.defaultConfig(type);
            assertEquals(type, cfg.fieldType());
            String json = cfg.toJson();
            assertFalse(json.isBlank());
            assertSame(type, FieldConfig.parse(type, json).fieldType());
        }
    }
}
