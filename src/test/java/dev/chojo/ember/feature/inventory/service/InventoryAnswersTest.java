/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.inventory.service;

import dev.chojo.ember.feature.inventory.entity.FieldConfig;
import dev.chojo.ember.feature.inventory.entity.FieldType;
import dev.chojo.ember.feature.inventory.entity.Inventory;
import dev.chojo.ember.feature.inventory.entity.InventoryItem;
import dev.chojo.ember.feature.inventory.entity.InventoryItemMetadata;
import dev.chojo.ember.feature.inventory.entity.InventoryType;
import dev.chojo.ember.feature.inventory.entity.ItemFieldValues;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import io.javalin.http.BadRequestResponse;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * What a piece of gear takes in its own fields, and how the piece keeps it.
 *
 * <p>First written down before the field types were brought together. A number carries a fraction
 * only where its step is below one now, as the screen already offers it.
 */
class InventoryAnswersTest extends RepositoryTestBase {
    private static final AtomicInteger NAMES = new AtomicInteger();

    private static InventoryService service;
    private static Station station;

    @BeforeAll
    static void setup() {
        service = new InventoryService(
                inventoryRepo,
                artRepo,
                fieldDefinitionService,
                itemCustodyService,
                clusterRepo,
                clusterStationGroupRepo);
        station = stationRepo.create("InventoryAnswersStation");
    }

    @AfterAll
    static void cleanup() {
        stationRepo.delete(station.id());
    }

    private static Inventory drawer() {
        return inventoryRepo.create(
                station.id(), "Schrank " + NAMES.incrementAndGet(), InventoryType.INTERNAL, false, false);
    }

    /** Writes one value onto a fresh piece of a drawer that describes the key with the given field. */
    private static InventoryItem written(
            FieldType type, FieldConfig config, boolean required, String key, ItemFieldValues.FieldValue value) {
        Inventory drawer = drawer();
        fieldDefinitionService.create(drawer.id(), key, "Feld " + key, type, required, 0, config);
        InventoryItem piece = service.createItem(drawer.id(), null, "Stück", null, InventoryItemMetadata.empty());
        var metadata = new InventoryItemMetadata(new ItemFieldValues(value == null ? Map.of() : Map.of(key, value)));
        return service.updateItem(piece.id(), null, "Stück", null, metadata, null)
                .orElseThrow();
    }

    private static String storedMetadata(InventoryItem piece) {
        return query("SELECT metadata::TEXT AS metadata FROM inventory_item WHERE id = :id;")
                .single(call().bind("id", piece.id()))
                .map(row -> row.getString("metadata"))
                .first()
                .orElseThrow();
    }

    private static FieldConfig.NumberConfig bounds(String min, String max) {
        return new FieldConfig.NumberConfig(new BigDecimal(min), new BigDecimal(max), new BigDecimal("0.5"), "kg");
    }

    @Test
    void aNumberWhoseStepIsBelowOneTakesAFractionBetweenItsBounds() {
        var piece = written(
                FieldType.NUMBER,
                bounds("0", "20"),
                false,
                "weight",
                new ItemFieldValues.ItemNumberValue(new BigDecimal("12.5")));

        assertEquals("{\"fields\": {\"weight\": {\"kind\": \"NUMBER\", \"value\": 12.5}}}", storedMetadata(piece));
    }

    @Test
    void aNumberWithoutAStepIsWhole() {
        var count = new FieldConfig.NumberConfig(null, null, null, "");

        var piece = written(
                FieldType.NUMBER, count, false, "count", new ItemFieldValues.ItemNumberValue(new BigDecimal("3")));
        assertEquals("{\"fields\": {\"count\": {\"kind\": \"NUMBER\", \"value\": 3}}}", storedMetadata(piece));

        assertThrows(
                BadRequestResponse.class,
                () -> written(
                        FieldType.NUMBER,
                        count,
                        false,
                        "count",
                        new ItemFieldValues.ItemNumberValue(new BigDecimal("2.5"))));
    }

    @Test
    void aNumberOutsideItsBoundsIsRefused() {
        assertThrows(
                BadRequestResponse.class,
                () -> written(
                        FieldType.NUMBER,
                        bounds("0", "20"),
                        false,
                        "weight",
                        new ItemFieldValues.ItemNumberValue(new BigDecimal("20.5"))));
    }

    @Test
    void aChoiceTakesItsValueAndRefusesItsLabel() {
        var options = new FieldConfig.EnumConfig(List.of(
                new FieldConfig.EnumConfig.EnumOption("blue", "Blau"),
                new FieldConfig.EnumConfig.EnumOption("red", "Rot")));

        var piece = written(FieldType.ENUM, options, false, "colour", new ItemFieldValues.ItemEnumValue("blue"));
        assertEquals("{\"fields\": {\"colour\": {\"kind\": \"ENUM\", \"value\": \"blue\"}}}", storedMetadata(piece));

        assertThrows(
                BadRequestResponse.class,
                () -> written(FieldType.ENUM, options, false, "colour", new ItemFieldValues.ItemEnumValue("Blau")));
    }

    @Test
    void aTextIgnoresItsLengthAndARequiredFieldMayStayEmpty() {
        var shortText = new FieldConfig.TextConfig(false, 3);

        var piece = written(FieldType.TEXT, shortText, true, "note", new ItemFieldValues.ItemTextValue("viel zu lang"));
        assertEquals(
                "{\"fields\": {\"note\": {\"kind\": \"TEXT\", \"value\": \"viel zu lang\"}}}", storedMetadata(piece));

        assertDoesNotThrow(() -> written(FieldType.TEXT, shortText, true, "note", null));
    }

    @Test
    void aDayAndAYesOrNoAreKeptTyped() {
        var day = written(
                FieldType.DATE, null, false, "checked", new ItemFieldValues.ItemDateValue(LocalDate.of(2026, 3, 9)));
        assertEquals(
                "{\"fields\": {\"checked\": {\"kind\": \"DATE\", \"value\": \"2026-03-09\"}}}", storedMetadata(day));

        var yes = written(FieldType.BOOLEAN, null, false, "sealed", new ItemFieldValues.ItemBooleanValue(true));
        assertEquals("{\"fields\": {\"sealed\": {\"kind\": \"BOOLEAN\", \"value\": true}}}", storedMetadata(yes));
    }
}
