/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.api.spec;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.fasterxml.jackson.annotation.JsonValue;
import dev.chojo.ember.api.ApiJsonMapper;
import dev.chojo.ember.api.spec.WireSchemas.Direction;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WireSchemasTest {

    private static final JsonMapper MAPPER = ApiJsonMapper.create();

    record Holder(
            int stationId,
            Integer clusterId,
            int count,
            String name,
            @Nullable String note,
            Instant at,
            List<Label> labels,
            Map<String, Integer> tally,
            JsonNode anything) {}

    enum Label {
        RED,
        GREEN
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record Sparse(@Nullable String note, String name, int size) {}

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "kind")
    @JsonSubTypes({
        @JsonSubTypes.Type(value = Circle.class, name = "circle"),
        @JsonSubTypes.Type(value = Square.class, name = "square")
    })
    sealed interface Shape permits Circle, Square {}

    record Circle(int radius) implements Shape {}

    record Square(int side) implements Shape {}

    sealed interface Setting permits Plain, Fancy {}

    record Plain(boolean on) implements Setting {}

    record Fancy(String style) implements Setting {}

    enum SettingType {
        PLAIN(Plain.class),
        FANCY(Fancy.class);

        private final Class<? extends Setting> settingClass;

        SettingType(Class<? extends Setting> settingClass) {
            this.settingClass = settingClass;
        }

        public Class<? extends Setting> settingClass() {
            return settingClass;
        }
    }

    record Configured(SettingType type, Setting setting) {}

    record Request(String name, int size, @Nullable String note) {}

    record Clearing(
            @Nullable Integer parentId,
            @Nullable Label label,
            @Nullable Counts counts,
            Label kept) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record SparseRequest(@Nullable String color) {}

    record Wrapper(@JsonValue Map<String, Integer> values) {}

    record Counts(int members, int stations) {}

    record Statistics(String label, @JsonUnwrapped Counts counts) {}

    static final class First {
        record Twin(int value) {}
    }

    static final class Second {
        record Twin(String value) {}
    }

    record Twins(First.Twin first, Second.Twin second) {}

    @Test
    void stationAndClusterIdsAreUuidStrings() {
        ObjectNode holder = component(Holder.class);

        assertEquals(uuid(), property(holder, "stationId"));
        assertEquals(uuid(), property(holder, "clusterId"));
        assertEquals("integer", property(holder, "count").get("type").asString());
    }

    @Test
    void referencesAreRequiredAndNeverNullUnlessMarkedNullable() {
        ObjectNode holder = component(Holder.class);

        assertTrue(required(holder, "name"));
        assertEquals("string", property(holder, "name").get("type").asString());
        assertTrue(required(holder, "note"));
        assertEquals(
                "[\"string\",\"null\"]", property(holder, "note").get("type").toString());
    }

    @Test
    void aNullablePropertyTheMapperLeavesOutIsOptionalRatherThanNull() {
        ObjectNode sparse = component(Sparse.class);

        assertFalse(required(sparse, "note"));
        assertEquals("string", property(sparse, "note").get("type").asString());
        assertTrue(required(sparse, "name"));
        assertTrue(required(sparse, "size"));
    }

    @Test
    void datesAreNamedAliasesAndContainersFollowTheirContent() {
        var schemas = new WireSchemas(MAPPER);
        schemas.schemaOf(Holder.class, Direction.RESPONSE);
        var components = schemas.components();
        ObjectNode holder = components.get("Holder");

        assertEquals(
                "#/components/schemas/Instant",
                property(holder, "at").get("$ref").asString());
        assertEquals("date-time", components.get("Instant").get("format").asString());
        assertEquals(
                "#/components/schemas/Label",
                property(holder, "labels").get("items").get("$ref").asString());
        assertEquals("[\"RED\",\"GREEN\"]", components.get("Label").get("enum").toString());
        assertEquals(
                "integer",
                property(holder, "tally")
                        .get("additionalProperties")
                        .get("type")
                        .asString());
        assertTrue(property(holder, "anything").isEmpty());
    }

    @Test
    void aTypeIdHierarchyIsADiscriminatedUnion() {
        var schemas = new WireSchemas(MAPPER);
        schemas.schemaOf(Shape.class, Direction.RESPONSE);
        var components = schemas.components();
        ObjectNode shape = components.get("Shape");

        assertEquals(2, shape.get("oneOf").size());
        assertEquals("kind", shape.get("discriminator").get("propertyName").asString());
        assertEquals(
                "#/components/schemas/Circle",
                shape.get("discriminator").get("mapping").get("circle").asString());
        ObjectNode circle = components.get("Circle");
        assertEquals("[\"circle\"]", property(circle, "kind").get("enum").toString());
        assertTrue(required(circle, "kind"));
    }

    @Test
    void aSealedHierarchyWithoutTypeIdIsAUnionWithAnObjectByType() {
        var schemas = new WireSchemas(MAPPER);
        schemas.schemaOf(Configured.class, Direction.RESPONSE);
        var components = schemas.components();

        assertEquals(
                "[{\"$ref\":\"#/components/schemas/Fancy\"},{\"$ref\":\"#/components/schemas/Plain\"}]",
                components.get("Setting").get("oneOf").toString());
        ObjectNode byType = components.get("SettingByType");
        assertEquals(
                "#/components/schemas/Plain",
                byType.get("properties").get("PLAIN").get("$ref").asString());
        assertEquals("[\"PLAIN\",\"FANCY\"]", byType.get("required").toString());
    }

    @Test
    void aTypeOnlyReadAsARequestHasEveryPropertyOptional() {
        var schemas = new WireSchemas(MAPPER);
        schemas.schemaOf(Request.class, Direction.REQUEST);
        ObjectNode request = schemas.components().get("Request");

        assertFalse(request.has("required"));
        assertEquals(
                "[\"string\",\"null\"]", property(request, "note").get("type").toString());
    }

    @Test
    void aNullableRequestPropertyIsOptionalAndTakesNullWhateverItHolds() {
        var schemas = new WireSchemas(MAPPER);
        schemas.schemaOf(Clearing.class, Direction.REQUEST);
        var components = schemas.components();
        ObjectNode clearing = components.get("Clearing");

        assertFalse(clearing.has("required"));
        assertEquals(
                "[\"integer\",\"null\"]",
                property(clearing, "parentId").get("type").toString());
        assertEquals(
                "[{\"$ref\":\"#/components/schemas/Label\"},{\"type\":\"null\"}]",
                property(clearing, "label").get("anyOf").toString());
        assertEquals(
                "[{\"$ref\":\"#/components/schemas/Counts\"},{\"type\":\"null\"}]",
                property(clearing, "counts").get("anyOf").toString());
        assertEquals(
                "#/components/schemas/Label",
                property(clearing, "kept").get("$ref").asString());
        assertEquals("[\"RED\",\"GREEN\"]", components.get("Label").get("enum").toString());
    }

    @Test
    void aRequestTakesNullEvenWhereTheMapperLeavesNullOutOfWhatItWrites() {
        var schemas = new WireSchemas(MAPPER);
        schemas.schemaOf(SparseRequest.class, Direction.REQUEST);
        ObjectNode request = schemas.components().get("SparseRequest");

        assertFalse(request.has("required"));
        assertEquals(
                "[\"string\",\"null\"]", property(request, "color").get("type").toString());
    }

    @Test
    void aTypeBothSentAndReadIsDescribedAsSent() {
        var schemas = new WireSchemas(MAPPER);
        schemas.schemaOf(Request.class, Direction.RESPONSE);
        schemas.schemaOf(Request.class, Direction.REQUEST);

        assertTrue(required(schemas.components().get("Request"), "name"));
    }

    @Test
    void aJsonValueIsDescribedAsTheValueItWrites() {
        ObjectNode wrapper = component(Wrapper.class);

        assertEquals("object", wrapper.get("type").asString());
        assertEquals("integer", wrapper.get("additionalProperties").get("type").asString());
    }

    @Test
    void anUnwrappedPropertyLendsItsPropertiesToTheOwner() {
        ObjectNode statistics = component(Statistics.class);

        assertTrue(required(statistics, "members"));
        assertTrue(required(statistics, "stations"));
        assertFalse(statistics.get("properties").has("counts"));
    }

    @Test
    void twoTypesWithOneNameFailTheGeneration() {
        var schemas = new WireSchemas(MAPPER);
        schemas.schemaOf(Twins.class, Direction.RESPONSE);

        var failure = assertThrows(IllegalStateException.class, schemas::components);
        assertTrue(failure.getMessage().contains("Twin: "), failure.getMessage());
    }

    @Test
    void everyScalarIsWrittenAsItsSchemaSays() {
        Map<Class<?>, Object> samples = Map.ofEntries(
                Map.entry(String.class, "text"),
                Map.entry(char.class, 'c'),
                Map.entry(Character.class, 'c'),
                Map.entry(Locale.class, Locale.GERMANY),
                Map.entry(boolean.class, true),
                Map.entry(Boolean.class, true),
                Map.entry(int.class, 1),
                Map.entry(Integer.class, 1),
                Map.entry(short.class, (short) 1),
                Map.entry(Short.class, (short) 1),
                Map.entry(byte.class, (byte) 1),
                Map.entry(Byte.class, (byte) 1),
                Map.entry(long.class, 1L),
                Map.entry(Long.class, 1L),
                Map.entry(BigInteger.class, BigInteger.ONE),
                Map.entry(float.class, 1.5f),
                Map.entry(Float.class, 1.5f),
                Map.entry(double.class, 1.5),
                Map.entry(Double.class, 1.5),
                Map.entry(BigDecimal.class, BigDecimal.ONE),
                Map.entry(UUID.class, UUID.randomUUID()),
                Map.entry(byte[].class, new byte[] {1}),
                Map.entry(URI.class, URI.create("https://example.org")),
                Map.entry(Instant.class, Instant.EPOCH),
                Map.entry(OffsetDateTime.class, OffsetDateTime.of(2026, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC)),
                Map.entry(LocalDateTime.class, LocalDateTime.of(2026, 1, 1, 0, 0)),
                Map.entry(LocalDate.class, LocalDate.of(2026, 1, 1)),
                Map.entry(LocalTime.class, LocalTime.NOON),
                Map.entry(Duration.class, Duration.ofMinutes(5)),
                Map.entry(ZoneId.class, ZoneId.of("Europe/Berlin")));

        WireSchemas.scalarSchemas().forEach((type, schema) -> {
            JsonNode written = MAPPER.readTree(MAPPER.writeValueAsString(samples.get(type)));
            String expected = schema.get("type").asString();
            String actual = written.isIntegralNumber()
                    ? "integer"
                    : written.getNodeType().name().toLowerCase(Locale.ROOT);
            assertTrue(
                    expected.equals(actual) || ("number".equals(expected) && "integer".equals(actual)),
                    type + " is described as " + expected + " but written as " + written);
        });
    }

    private static ObjectNode component(Class<?> type) {
        var schemas = new WireSchemas(MAPPER);
        schemas.schemaOf(type, Direction.RESPONSE);
        return schemas.components().get(type.getSimpleName());
    }

    private static JsonNode property(ObjectNode schema, String name) {
        return schema.get("properties").get(name);
    }

    private static boolean required(ObjectNode schema, String name) {
        JsonNode required = schema.get("required");
        if (required == null) return false;
        for (JsonNode entry : required) {
            if (entry.asString().equals(name)) return true;
        }
        return false;
    }

    private static ObjectNode uuid() {
        return MAPPER.createObjectNode().put("type", "string").put("format", "uuid");
    }
}
