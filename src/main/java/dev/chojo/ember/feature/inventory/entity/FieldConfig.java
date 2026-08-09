/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.inventory.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.util.List;

/**
 * Typed config for an {@link InventoryFieldDefinition}, varying by
 * {@link FieldType}. Serialised as JSONB in {@code inventory_field_definition.config}.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "kind")
@JsonSubTypes({
    @JsonSubTypes.Type(value = FieldConfig.DateConfig.class, name = "DATE"),
    @JsonSubTypes.Type(value = FieldConfig.EnumConfig.class, name = "ENUM"),
    @JsonSubTypes.Type(value = FieldConfig.TextConfig.class, name = "TEXT"),
    @JsonSubTypes.Type(value = FieldConfig.NumberConfig.class, name = "NUMBER"),
    @JsonSubTypes.Type(value = FieldConfig.BooleanConfig.class, name = "BOOLEAN")
})
public sealed interface FieldConfig
        permits FieldConfig.DateConfig,
                FieldConfig.EnumConfig,
                FieldConfig.TextConfig,
                FieldConfig.NumberConfig,
                FieldConfig.BooleanConfig {

    /**
     * Shared Jackson mapper for serialising and parsing field config variants.
     */
    JsonMapper MAPPER = JsonMapper.builder().build();

    /**
     * Parses the {@code config} JSONB for the given field type. The payload is
     * self-describing through its {@code kind} discriminator; the given field type
     * only cross-checks that the parsed variant belongs to the field.
     *
     * @param fieldType the field type the config must belong to
     * @param json      raw JSONB string, may be null or blank for an empty config
     * @return the parsed config; defaults for the type are returned on missing keys
     */
    static FieldConfig parse(FieldType fieldType, String json) {
        FieldConfig parsed;
        try {
            String payload = json == null || json.isBlank() ? "{\"kind\": \"%s\"}".formatted(fieldType.name()) : json;
            parsed = MAPPER.readValue(payload, FieldConfig.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid inventory_field_definition config payload", e);
        }
        if (parsed.fieldType() != fieldType) {
            throw new IllegalArgumentException("Field config kind does not match the field type");
        }
        return parsed;
    }

    /**
     * Serialises a possibly-absent config to the JSONB payload the
     * {@code inventory_field_definition.config} column expects, falling back to
     * an empty object for {@code null}.
     *
     * @param config the config to serialise, may be null
     * @return the JSONB payload, never null
     */
    static String toJsonOrEmpty(FieldConfig config) {
        return config == null ? "{}" : config.toJson();
    }

    /**
     * Returns the {@link FieldType} this config variant applies to.
     */
    FieldType fieldType();

    /**
     * Serialises this config to JSONB form for {@code inventory_field_definition.config}.
     */
    default String toJson() {
        try {
            return MAPPER.writeValueAsString(this);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize FieldConfig", e);
        }
    }

    /**
     * Config for {@link FieldType#DATE} — no extra knobs in v1.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record DateConfig() implements FieldConfig {
        @Override
        public FieldType fieldType() {
            return FieldType.DATE;
        }
    }

    /**
     * Config for {@link FieldType#ENUM}.
     *
     * @param options the allowed values, in display order
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record EnumConfig(List<EnumOption> options) implements FieldConfig {
        /**
         * Convenience constructor with empty option list.
         */
        public EnumConfig {
            options = options == null ? List.of() : List.copyOf(options);
        }

        @Override
        public FieldType fieldType() {
            return FieldType.ENUM;
        }

        /**
         * One option in an ENUM field.
         *
         * @param value stable machine value persisted on the item
         * @param label display label shown in pickers and lists
         */
        @JsonIgnoreProperties(ignoreUnknown = true)
        public record EnumOption(String value, String label) {}
    }

    /**
     * Config for {@link FieldType#TEXT}.
     *
     * @param multiline whether the editor renders a textarea instead of a single-line input
     * @param maxLength upper bound on the stored string length, in characters
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record TextConfig(boolean multiline, int maxLength) implements FieldConfig {
        @Override
        public FieldType fieldType() {
            return FieldType.TEXT;
        }
    }

    /**
     * Config for {@link FieldType#NUMBER}.
     *
     * @param min  inclusive minimum, or {@code null} for unbounded
     * @param max  inclusive maximum, or {@code null} for unbounded
     * @param step input step hint, or {@code null} for free-form
     * @param unit display unit (e.g. "kg", "cm"), may be empty
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record NumberConfig(BigDecimal min, BigDecimal max, BigDecimal step, String unit) implements FieldConfig {
        @Override
        public FieldType fieldType() {
            return FieldType.NUMBER;
        }
    }

    /**
     * Config for {@link FieldType#BOOLEAN}.
     *
     * @param trueLabel  label shown for the {@code true} state
     * @param falseLabel label shown for the {@code false} state
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record BooleanConfig(String trueLabel, String falseLabel) implements FieldConfig {
        @Override
        public FieldType fieldType() {
            return FieldType.BOOLEAN;
        }
    }
}
