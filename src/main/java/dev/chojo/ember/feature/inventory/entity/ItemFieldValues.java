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
     */
    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "kind")
    @JsonSubTypes({
        @JsonSubTypes.Type(value = DateValue.class, name = "DATE"),
        @JsonSubTypes.Type(value = EnumValue.class, name = "ENUM"),
        @JsonSubTypes.Type(value = TextValue.class, name = "TEXT"),
        @JsonSubTypes.Type(value = NumberValue.class, name = "NUMBER"),
        @JsonSubTypes.Type(value = BooleanValue.class, name = "BOOLEAN")
    })
    public sealed interface FieldValue permits DateValue, EnumValue, TextValue, NumberValue, BooleanValue {
        /**
         * Returns the {@link FieldType} this value variant carries.
         */
        FieldType fieldType();
    }

    /**
     * Value variant for {@link FieldType#DATE}.
     *
     * @param value the date, never {@code null}
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DateValue(LocalDate value) implements FieldValue {
        @Override
        public FieldType fieldType() {
            return FieldType.DATE;
        }
    }

    /**
     * Value variant for {@link FieldType#ENUM}.
     *
     * @param value the picked enum option value
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record EnumValue(String value) implements FieldValue {
        @Override
        public FieldType fieldType() {
            return FieldType.ENUM;
        }
    }

    /**
     * Value variant for {@link FieldType#TEXT}.
     *
     * @param value the text payload, may be empty
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TextValue(String value) implements FieldValue {
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
    public record NumberValue(BigDecimal value) implements FieldValue {
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
    public record BooleanValue(boolean value) implements FieldValue {
        @Override
        public FieldType fieldType() {
            return FieldType.BOOLEAN;
        }
    }
}
