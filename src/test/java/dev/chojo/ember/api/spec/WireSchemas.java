/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.api.spec;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import dev.chojo.ember.api.PublicIdModule;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.BeanDescription;
import tools.jackson.databind.DatabindContext;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.introspect.AnnotatedMember;
import tools.jackson.databind.introspect.BeanPropertyDefinition;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.jsontype.NamedType;
import tools.jackson.databind.jsontype.TypeSerializer;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.WildcardType;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Supplier;

/**
 * Describes Java types as the API's JSON mapper writes and reads them, as OpenAPI 3.1 schemas.
 *
 * <p>Every property comes out of the mapper's own introspection, so names, ignored accessors,
 * inclusion rules, {@code @JsonValue}, {@code @JsonUnwrapped} and polymorphic type ids are described as
 * Jackson applies them. The rules Jackson does not state are kept here, once:
 *
 * <ul>
 *   <li>A component named in {@link PublicIdModule#STATION_ID_FIELDS} or
 *       {@link PublicIdModule#CLUSTER_ID_FIELDS} and declared {@code int} or {@code Integer} is a UUID
 *       string, as that module writes it.
 *   <li>A reference property is never null unless it is marked {@link WireNullness nullable}; a
 *       primitive is always there. A nullable property the mapper leaves out when it is null is optional
 *       rather than nullable.
 *   <li>A type only ever read as a request body has every property optional, since the mapper reads a
 *       missing property as null or the primitive default. A property marked nullable also takes an
 *       explicit null, whatever the type's inclusion rule says about writing, because that is how a
 *       request names a value it clears.
 *   <li>A sealed hierarchy without a type id is the union of its records. An enum with an accessor
 *       returning the class of one of its members, the way a cell type names its config record, adds an
 *       object from each constant to that record, named {@code <Hierarchy>ByType}.
 *   <li>Dates are named aliases, so a field says what it holds.
 * </ul>
 *
 * <p>Components are named by the simple name of their type. Two types with one name cannot both be
 * described, and every such pair is reported by {@link #components()}.
 */
final class WireSchemas {

    /** Whether a type is written by the API or read from a request. */
    enum Direction {
        RESPONSE,
        REQUEST
    }

    private static final String REF_PREFIX = "#/components/schemas/";

    private static final Map<Class<?>, Scalar> SCALARS = scalars();

    private final JsonMapper mapper;
    private final SerializationContext writing;
    private final DatabindContext reading;
    private final JsonNodeFactory nodes = JsonNodeFactory.instance;
    private final Map<String, Object> owners = new LinkedHashMap<>();
    private final Map<String, ObjectNode> components = new TreeMap<>();
    private final Set<String> collisions = new TreeSet<>();
    private final Set<Object> unnamed = new HashSet<>();
    private final Set<Class<?>> enums = new TreeSet<>(Comparator.comparing(Class::getName));

    /**
     * @param mapper the mapper the API writes and reads with
     */
    WireSchemas(JsonMapper mapper) {
        this.mapper = mapper;
        this.writing = mapper._serializationContext();
        this.reading = mapper._deserializationContext();
    }

    /**
     * The schema of a type, registering the components it needs.
     *
     * @param type      the declared type
     * @param direction whether the type is written or read
     * @return an inline schema or a reference to a component
     */
    ObjectNode schemaOf(Type type, Direction direction) {
        return schemaOf(mapper.constructType(type), direction);
    }

    /**
     * Every component registered so far, with the by-type objects of the enums that choose a member of a
     * described hierarchy.
     *
     * @return the components, by name
     * @throws IllegalStateException when two types share a name
     */
    Map<String, ObjectNode> components() {
        addByTypeObjects();
        if (!collisions.isEmpty()) {
            throw new IllegalStateException(
                    "Wire types share a name; rename one of each pair:\n  " + String.join("\n  ", collisions));
        }
        return components;
    }

    private ObjectNode schemaOf(JavaType type, Direction direction) {
        Class<?> raw = type.getRawClass();
        Scalar scalar = SCALARS.get(raw);
        if (scalar != null) return scalar.alias() ? aliasOf(raw, scalar) : scalar.schema(nodes);
        if (type.isReferenceType()) return nullable(schemaOf(type.getReferencedType(), direction));
        if (type.isArrayType() || type.isCollectionLikeType()) {
            ObjectNode array = nodes.objectNode().put("type", "array");
            array.set("items", schemaOf(type.getContentType(), direction));
            return array;
        }
        if (type.isMapLikeType()) {
            ObjectNode map = nodes.objectNode().put("type", "object");
            map.set("additionalProperties", schemaOf(type.getContentType(), direction));
            return map;
        }
        if (type.isJavaLangObject() || JsonNode.class.isAssignableFrom(raw)) return nodes.objectNode();
        if (type.isEnumType()) {
            Class<?> enumType = raw.isEnum() ? raw : raw.getSuperclass();
            return component(enumType.getSimpleName(), enumType, () -> enumSchema(enumType));
        }
        return component(raw.getSimpleName(), raw, () -> beanSchema(type, direction));
    }

    private ObjectNode component(String name, Object owner, Supplier<ObjectNode> builder) {
        Object known = owners.putIfAbsent(name, owner);
        if (known == null) {
            components.put(name, nodes.objectNode());
            components.put(name, builder.get());
        } else if (!known.equals(owner)) {
            collisions.add(name + ": " + known + " and " + owner);
            if (unnamed.add(owner)) builder.get();
        }
        return ref(name);
    }

    private ObjectNode aliasOf(Class<?> raw, Scalar scalar) {
        return component(raw.getSimpleName(), raw, () -> scalar.schema(nodes));
    }

    private ObjectNode ref(String name) {
        return nodes.objectNode().put("$ref", REF_PREFIX + name);
    }

    private ObjectNode enumSchema(Class<?> raw) {
        enums.add(raw);
        ArrayNode values = nodes.arrayNode();
        boolean textual = true;
        for (Object constant : raw.getEnumConstants()) {
            JsonNode value = mapper.valueToTree(constant);
            textual &= value.isString();
            values.add(value);
        }
        ObjectNode schema = nodes.objectNode().put("type", textual ? "string" : "integer");
        schema.set("enum", values);
        return schema;
    }

    private ObjectNode beanSchema(JavaType type, Direction direction) {
        BeanDescription description = writing.introspectBeanDescription(type);
        AnnotatedMember value = description.findJsonValueAccessor();
        if (value != null) return schemaOf(value.getType(), direction);
        TypeSerializer typed = writing.findTypeSerializer(type);
        if (typed != null) return polymorphic(type, typed, direction);
        Class<?> raw = type.getRawClass();
        if (raw.isSealed()) return union(concreteMembers(raw), direction);
        if (raw.isInterface() || Modifier.isAbstract(raw.getModifiers())) {
            throw new IllegalStateException(raw.getName()
                    + " is abstract without a type id or a sealed hierarchy, so its shape is decided at runtime");
        }
        return objectSchema(type, direction, null);
    }

    private ObjectNode polymorphic(JavaType type, TypeSerializer typed, Direction direction) {
        if (typed.getTypeInclusion() != JsonTypeInfo.As.PROPERTY) {
            throw new IllegalStateException(type.getRawClass().getName() + " includes its type id as "
                    + typed.getTypeInclusion() + "; only a property is described");
        }
        Class<?> raw = type.getRawClass();
        if (!raw.isInterface() && !Modifier.isAbstract(raw.getModifiers())) {
            return objectSchema(type, direction, new TypeId(typed.getPropertyName(), typeId(typed, raw)));
        }
        List<Class<?>> members = subtypes(type);
        ObjectNode union = union(members, direction);
        ObjectNode discriminator = union.putObject("discriminator").put("propertyName", typed.getPropertyName());
        ObjectNode mapping = discriminator.putObject("mapping");
        for (Class<?> member : members) {
            mapping.put(typeId(typed, member), REF_PREFIX + member.getSimpleName());
        }
        return union;
    }

    private String typeId(TypeSerializer typed, Class<?> member) {
        return typed.getTypeIdResolver().idFromValueAndType(writing, null, member);
    }

    private List<Class<?>> subtypes(JavaType type) {
        var config = writing.getConfig();
        Collection<NamedType> named = config.getSubtypeResolver()
                .collectAndResolveSubtypesByClass(config, writing.introspectClassAnnotations(type));
        return named.stream()
                .map(NamedType::getType)
                .filter(cls -> !cls.isInterface() && !Modifier.isAbstract(cls.getModifiers()))
                .distinct()
                .sorted(Comparator.comparing(Class::getSimpleName))
                .toList();
    }

    private ObjectNode union(List<Class<?>> members, Direction direction) {
        ObjectNode union = nodes.objectNode();
        ArrayNode oneOf = union.putArray("oneOf");
        for (Class<?> member : members) oneOf.add(schemaOf(member, direction));
        return union;
    }

    private static List<Class<?>> concreteMembers(Class<?> sealed) {
        List<Class<?>> members = new ArrayList<>();
        for (Class<?> permitted : sealed.getPermittedSubclasses()) {
            if (permitted.isSealed()) {
                members.addAll(concreteMembers(permitted));
            } else {
                members.add(permitted);
            }
        }
        members.sort(Comparator.comparing(Class::getSimpleName));
        return members;
    }

    private ObjectNode objectSchema(JavaType type, Direction direction, @Nullable TypeId typeId) {
        ObjectNode schema = nodes.objectNode().put("type", "object");
        ObjectNode properties = schema.putObject("properties");
        ArrayNode required = nodes.arrayNode();
        if (typeId != null) {
            properties
                    .putObject(typeId.property())
                    .put("type", "string")
                    .putArray("enum")
                    .add(typeId.id());
            required.add(typeId.property());
        }
        addProperties(type, direction, properties, required);
        if (!required.isEmpty()) schema.set("required", required);
        return schema;
    }

    private void addProperties(JavaType type, Direction direction, ObjectNode properties, ArrayNode required) {
        DatabindContext context = direction == Direction.RESPONSE ? writing : reading;
        BeanDescription description = context.introspectBeanDescription(type);
        Class<?> raw = type.getRawClass();
        JsonInclude.Value inclusion =
                description.findPropertyInclusion(writing.getConfig().getDefaultPropertyInclusion(raw));
        for (BeanPropertyDefinition property : description.findProperties()) {
            AnnotatedMember member = direction == Direction.RESPONSE ? property.getAccessor() : property.getMutator();
            if (member == null) continue;
            if (writing.getAnnotationIntrospector().findUnwrappingNameTransformer(writing.getConfig(), member)
                    != null) {
                addProperties(member.getType(), direction, properties, required);
                continue;
            }
            WireProperty wire = new WireProperty(
                    property.getName(),
                    member.getType(),
                    WireNullness.isNullable(raw, property),
                    omitsNull(inclusion.withOverrides(property.findInclusion())));
            properties.set(wire.name(), propertySchema(wire, direction));
            if (wire.required(direction)) required.add(wire.name());
        }
    }

    private ObjectNode propertySchema(WireProperty property, Direction direction) {
        ObjectNode schema = isPublicId(property)
                ? nodes.objectNode().put("type", "string").put("format", "uuid")
                : schemaOf(property.type(), direction);
        return property.carriesNull(direction) ? nullable(schema) : schema;
    }

    private static boolean isPublicId(WireProperty property) {
        Class<?> raw = property.type().getRawClass();
        if (raw != int.class && raw != Integer.class) return false;
        return PublicIdModule.STATION_ID_FIELDS.contains(property.name())
                || PublicIdModule.CLUSTER_ID_FIELDS.contains(property.name());
    }

    private static boolean omitsNull(JsonInclude.Value inclusion) {
        return switch (inclusion.getValueInclusion()) {
            case ALWAYS, USE_DEFAULTS -> false;
            default -> true;
        };
    }

    private ObjectNode nullable(ObjectNode schema) {
        if (schema.has("type") && schema.get("type").isString()) {
            String type = schema.get("type").asString();
            schema.putArray("type").add(type).add("null");
            if (schema.has("enum")) ((ArrayNode) schema.get("enum")).addNull();
            return schema;
        }
        if (schema.isEmpty()) return schema;
        ObjectNode anyOf = nodes.objectNode();
        anyOf.putArray("anyOf").add(schema).add(nodes.objectNode().put("type", "null"));
        return anyOf;
    }

    private void addByTypeObjects() {
        for (Class<?> selector : List.copyOf(enums)) {
            for (var accessor : selector.getMethods()) {
                if (accessor.getParameterCount() != 0 || Modifier.isStatic(accessor.getModifiers())) continue;
                Class<?> hierarchy = selectedHierarchy(accessor.getGenericReturnType());
                if (hierarchy == null || owners.get(hierarchy.getSimpleName()) != hierarchy) continue;
                component(
                        hierarchy.getSimpleName() + "ByType",
                        selector.getName() + "#" + accessor.getName(),
                        () -> byType(selector, accessor));
            }
        }
    }

    private ObjectNode byType(Class<?> selector, Method accessor) {
        ObjectNode byType = nodes.objectNode().put("type", "object");
        ObjectNode properties = byType.putObject("properties");
        ArrayNode required = byType.putArray("required");
        for (Object constant : selector.getEnumConstants()) {
            String key = mapper.valueToTree(constant).asString();
            properties.set(key, schemaOf(selected(accessor, constant), Direction.RESPONSE));
            required.add(key);
        }
        return byType;
    }

    private static Class<?> selected(Method accessor, Object constant) {
        try {
            return (Class<?>) accessor.invoke(constant);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot read " + accessor + " of " + constant, e);
        }
    }

    private static @Nullable Class<?> selectedHierarchy(Type returned) {
        if (!(returned instanceof ParameterizedType parameterized) || parameterized.getRawType() != Class.class) {
            return null;
        }
        Type argument = parameterized.getActualTypeArguments()[0];
        if (!(argument instanceof WildcardType wildcard) || wildcard.getUpperBounds().length != 1) return null;
        return wildcard.getUpperBounds()[0] instanceof Class<?> bound && bound.isSealed() ? bound : null;
    }

    private static Map<Class<?>, Scalar> scalars() {
        Map<Class<?>, Scalar> scalars = new LinkedHashMap<>();
        for (Class<?> text : List.of(String.class, char.class, Character.class, java.util.Locale.class)) {
            scalars.put(text, Scalar.of("string", null));
        }
        for (Class<?> flag : List.of(boolean.class, Boolean.class)) scalars.put(flag, Scalar.of("boolean", null));
        for (Class<?> small : List.of(int.class, Integer.class, short.class, Short.class, byte.class, Byte.class)) {
            scalars.put(small, Scalar.of("integer", "int32"));
        }
        for (Class<?> large : List.of(long.class, Long.class, java.math.BigInteger.class)) {
            scalars.put(large, Scalar.of("integer", "int64"));
        }
        for (Class<?> number :
                List.of(float.class, Float.class, double.class, Double.class, java.math.BigDecimal.class)) {
            scalars.put(number, Scalar.of("number", null));
        }
        scalars.put(java.util.UUID.class, Scalar.of("string", "uuid"));
        scalars.put(byte[].class, Scalar.of("string", "byte"));
        scalars.put(java.net.URI.class, Scalar.of("string", "uri"));
        scalars.put(java.time.Instant.class, Scalar.alias("date-time"));
        scalars.put(java.time.OffsetDateTime.class, Scalar.alias("date-time"));
        scalars.put(java.time.LocalDateTime.class, Scalar.alias("date-time"));
        scalars.put(java.time.LocalDate.class, Scalar.alias("date"));
        scalars.put(java.time.LocalTime.class, Scalar.alias("time"));
        scalars.put(java.time.Duration.class, Scalar.alias("duration"));
        scalars.put(java.time.ZoneId.class, Scalar.of("string", null));
        return Map.copyOf(scalars);
    }

    /** The type id a member of a polymorphic hierarchy writes, and under which property. */
    private record TypeId(String property, String id) {}

    /**
     * A JSON type with an optional format, inline or as a named alias.
     *
     * @param type   the JSON type
     * @param format the format, if any
     * @param alias  whether it is described once, under the Java type's name
     */
    record Scalar(String type, @Nullable String format, boolean alias) {
        static Scalar of(String type, @Nullable String format) {
            return new Scalar(type, format, false);
        }

        static Scalar alias(String format) {
            return new Scalar("string", format, true);
        }

        ObjectNode schema(JsonNodeFactory nodes) {
            ObjectNode schema = nodes.objectNode().put("type", type);
            if (format != null) schema.put("format", format);
            return schema;
        }
    }

    /**
     * Every scalar Java type this generator maps, with the schema it maps to, for the test that has the
     * mapper write one of each.
     *
     * @return the schema of each scalar type
     */
    static Map<Class<?>, ObjectNode> scalarSchemas() {
        Map<Class<?>, ObjectNode> schemas = new LinkedHashMap<>();
        SCALARS.forEach((type, scalar) -> schemas.put(type, scalar.schema(JsonNodeFactory.instance)));
        return schemas;
    }
}
