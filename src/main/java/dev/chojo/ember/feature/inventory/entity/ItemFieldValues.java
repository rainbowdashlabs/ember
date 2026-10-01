/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.inventory.entity;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonValue;
import dev.chojo.ember.feature.question.FieldType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Custom-field values attached to a single inventory item, keyed by the
 * field's {@code key}. Serialised inside {@link InventoryItemMetadata} under
 * the {@code fields} property.
 *
 * @param values the per-key value map, may be empty
 */
public record ItemFieldValues(@JsonValue Map<String, FieldValue> values) {

    /**
     * Returns an empty value set.
     */
    public static ItemFieldValues empty() {
        return new ItemFieldValues(Map.of());
    }

    /**
     * Jackson-side factory that lets {@link InventoryItemMetadata} deserialise
     * the {@code fields} property directly as a key→value map (rather than as
     * a wrapper object).
     */
    @JsonCreator
    public static ItemFieldValues fromMap(Map<String, FieldValue> values) {
        return new ItemFieldValues(values);
    }

    /**
     * Normalises the value map to an immutable copy preserving insertion order.
     */
    public ItemFieldValues {
        values = values == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(values));
    }

    /**
     * Typed value for a single custom-field entry. The {@code kind}
     * discriminator drives Jackson's polymorphic deserialization.
     *
     * <p>The kind is the shared field type name. A choice answered before the names were shared
     * says {@code ENUM}, which is still read.
     */
    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "kind")
    @JsonSubTypes({
        @JsonSubTypes.Type(value = ItemDateValue.class, name = "DATE"),
        @JsonSubTypes.Type(
                value = ItemEnumValue.class,
                name = "CHOICE",
                names = {"ENUM", "CHOICE"}),
        @JsonSubTypes.Type(value = ItemTextValue.class, name = "TEXT"),
        @JsonSubTypes.Type(value = ItemNumberValue.class, name = "NUMBER"),
        @JsonSubTypes.Type(value = ItemBooleanValue.class, name = "BOOLEAN")
    })
    public sealed interface FieldValue
            permits ItemDateValue, ItemEnumValue, ItemTextValue, ItemNumberValue, ItemBooleanValue {
        /**
         * Returns the {@link FieldType} this value variant carries.
         */
        FieldType fieldType();

        /**
         * The value as plain text, which is what measuring it against the field describing it asks
         * for.
         */
        default String asText() {
            return switch (this) {
                case ItemDateValue(var date) -> date == null ? "" : date.toString();
                case ItemEnumValue(var picked) -> picked == null ? "" : picked;
                case ItemTextValue(var text) -> text == null ? "" : text;
                case ItemNumberValue(var number) -> number == null ? "" : number.toPlainString();
                case ItemBooleanValue(var yes) -> String.valueOf(yes);
            };
        }
    }

    /**
     * Value variant for {@link FieldType#DATE}.
     *
     * @param value the date, never {@code null}
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ItemDateValue(LocalDate value) implements FieldValue {
        @Override
        public FieldType fieldType() {
            return FieldType.DATE;
        }
    }

    /**
     * Value variant for {@link FieldType#CHOICE}.
     *
     * @param value the picked option value
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ItemEnumValue(String value) implements FieldValue {
        @Override
        public FieldType fieldType() {
            return FieldType.CHOICE;
        }
    }

    /**
     * Value variant for {@link FieldType#TEXT}.
     *
     * @param value the text payload, may be empty
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ItemTextValue(String value) implements FieldValue {
        @Override
        public FieldType fieldType() {
            return FieldType.TEXT;
        }
    }

    /**
     * Value variant for {@link FieldType#NUMBER}.
     *
     * @param value the numeric payload
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ItemNumberValue(BigDecimal value) implements FieldValue {
        @Override
        public FieldType fieldType() {
            return FieldType.NUMBER;
        }
    }

    /**
     * Value variant for {@link FieldType#BOOLEAN}.
     *
     * @param value the boolean payload
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ItemBooleanValue(boolean value) implements FieldValue {
        @Override
        public FieldType fieldType() {
            return FieldType.BOOLEAN;
        }
    }
}
