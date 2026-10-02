/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.api;

import dev.chojo.ember.feature.cluster.entity.Cluster;
import dev.chojo.ember.feature.cluster.repository.ClusterRepository;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.repository.StationRepository;
import org.jspecify.annotations.Nullable;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.BeanDescription;
import tools.jackson.databind.DeserializationConfig;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.SerializationConfig;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.deser.BeanDeserializerBuilder;
import tools.jackson.databind.deser.SettableBeanProperty;
import tools.jackson.databind.deser.ValueDeserializerModifier;
import tools.jackson.databind.module.SimpleModule;
import tools.jackson.databind.ser.BeanPropertyWriter;
import tools.jackson.databind.ser.ValueSerializerModifier;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.IntFunction;

/**
 * Translates the internal {@code int} ids of stations and clusters to the UUIDs the API exposes, and
 * back.
 *
 * <p>Inside the backend a station or a cluster is an {@code int}; on the wire it is its public UUID.
 * Which record components carry such an id is decided here, by name, in {@link #STATION_ID_FIELDS} and
 * {@link #CLUSTER_ID_FIELDS}, and only for components declared {@code int} or {@code Integer}. Both
 * directions matter: without the reading half, a body the API itself produced would come back with a
 * UUID string where an {@code int} is declared and fail to bind.
 *
 * <p>A response from a federation partner is read with {@link #forPartnerResponses()}. The partner
 * wrote its own station ids as UUIDs, and those mean nothing on this instance, so every station id is
 * read as the neutral value ({@code 0}, or {@code null} where the component is boxed) rather than
 * resolved. Resolving it would be worse than useless: a partner could name any station on this
 * instance and have its id land inside an entity that is then trusted.
 */
public final class PublicIdModule extends SimpleModule {

    /** The names of the components that hold a station id. */
    public static final Set<String> STATION_ID_FIELDS =
            Set.of("stationId", "sourceStationId", "partnerStationId", "owningStationId", "homeStationId");

    /** The names of the components that hold a cluster id. */
    public static final Set<String> CLUSTER_ID_FIELDS = Set.of("clusterId", "ownerClusterId");

    private final List<IdKind> kinds;

    private PublicIdModule(String name, List<IdKind> kinds) {
        super(name);
        this.kinds = kinds;
    }

    /**
     * The translation at the API boundary: station and cluster ids leave as UUIDs and come back as
     * the internal ids they name. An unknown or malformed UUID fails the read.
     *
     * @param stations resolves station ids and UUIDs
     * @param clusters resolves cluster ids and UUIDs
     * @return the module for the API mapper
     */
    public static PublicIdModule forApi(StationRepository stations, ClusterRepository clusters) {
        return new PublicIdModule(
                "PublicIdModule",
                List.of(
                        new IdKind("station", STATION_ID_FIELDS, stations::resolveUid, uid -> stations.findByUid(uid)
                                .map(Station::id)),
                        new IdKind("cluster", CLUSTER_ID_FIELDS, clusters::resolveUid, uid -> clusters.findByUid(uid)
                                .map(Cluster::id))));
    }

    /**
     * The reading of a federation partner's response: every station id is read as the neutral value,
     * whatever the partner sent for it. Nothing is written differently.
     *
     * @return the module for mappers that read partner responses
     */
    public static PublicIdModule forPartnerResponses() {
        return new PublicIdModule(
                "PartnerStationIdModule", List.of(new IdKind("station", STATION_ID_FIELDS, null, null)));
    }

    @Override
    public void setupModule(SetupContext context) {
        super.setupModule(context);
        context.addSerializerModifier(new IdSerializerModifier());
        context.addDeserializerModifier(new IdDeserializerModifier());
    }

    private Optional<IdKind> kindOf(String propertyName, Class<?> type) {
        if (type != int.class && type != Integer.class) return Optional.empty();
        return kinds.stream()
                .filter(kind -> kind.fields().contains(propertyName))
                .findFirst();
    }

    /**
     * One kind of id and how it is translated.
     *
     * @param label      how an error message names it
     * @param fields     the component names that carry it
     * @param toPublic   the UUID of an internal id, or {@code null} where the id is written as it is
     * @param toInternal the internal id of a UUID, or {@code null} where every id is read as neutral
     */
    private record IdKind(
            String label,
            Set<String> fields,
            @Nullable IntFunction<UUID> toPublic,
            @Nullable Function<UUID, Optional<Integer>> toInternal) {

        @Nullable
        Integer read(String raw, String propertyName) {
            if (toInternal == null) return null;
            UUID uid;
            try {
                uid = UUID.fromString(raw);
            } catch (IllegalArgumentException ex) {
                throw new IllegalArgumentException(
                        "Cannot parse " + label + " id '" + propertyName + "': '" + raw + "' is not a UUID");
            }
            return toInternal
                    .apply(uid)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Unknown " + label + " id '" + propertyName + "' (uid " + uid + ")"));
        }
    }

    private final class IdSerializerModifier extends ValueSerializerModifier {
        @Override
        public List<BeanPropertyWriter> changeProperties(
                SerializationConfig config,
                BeanDescription.Supplier beanDescSupplier,
                List<BeanPropertyWriter> beanProperties) {
            var result = new ArrayList<BeanPropertyWriter>(beanProperties.size());
            for (var prop : beanProperties) {
                var kind = kindOf(prop.getName(), prop.getType().getRawClass());
                if (kind.isPresent() && kind.get().toPublic() != null) {
                    result.add(new PublicIdPropertyWriter(prop, kind.get().toPublic()));
                } else {
                    result.add(prop);
                }
            }
            return result;
        }
    }

    private static final class PublicIdPropertyWriter extends BeanPropertyWriter {
        private final IntFunction<UUID> toPublic;

        PublicIdPropertyWriter(BeanPropertyWriter base, IntFunction<UUID> toPublic) {
            super(base);
            this.toPublic = toPublic;
        }

        @Override
        public void serializeAsProperty(Object bean, JsonGenerator gen, SerializationContext ctxt) throws Exception {
            Object value = get(bean);
            gen.writeName(getName());
            UUID uid = value == null ? null : toPublic.apply(((Number) value).intValue());
            if (uid == null) {
                gen.writeNull();
            } else {
                gen.writeString(uid.toString());
            }
        }
    }

    /**
     * Swaps the deserializer of every matching component, collecting the replacements first so the
     * builder is not changed while it is walked.
     */
    private final class IdDeserializerModifier extends ValueDeserializerModifier {
        @Override
        public BeanDeserializerBuilder updateBuilder(
                DeserializationConfig config,
                BeanDescription.Supplier beanDescSupplier,
                BeanDeserializerBuilder builder) {
            List<SettableBeanProperty> replacements = new ArrayList<>();
            Iterator<SettableBeanProperty> it = builder.getProperties();
            while (it.hasNext()) {
                SettableBeanProperty prop = it.next();
                Class<?> raw = prop.getType().getRawClass();
                kindOf(prop.getName(), raw)
                        .ifPresent(kind -> replacements.add(prop.withValueDeserializer(
                                new PublicIdDeserializer(kind, prop.getName(), raw == int.class))));
            }
            for (SettableBeanProperty replacement : replacements) {
                builder.addOrReplaceProperty(replacement, true);
            }
            return builder;
        }
    }

    /**
     * Reads a UUID string into the internal id. Where nothing is there, or the kind reads every id as
     * neutral, a primitive component gets {@code 0}, which is Jackson's own default for a missing
     * {@code int}, and a boxed one {@code null}.
     */
    private static final class PublicIdDeserializer extends ValueDeserializer<Integer> {
        private final IdKind kind;
        private final String propertyName;
        private final boolean primitive;

        PublicIdDeserializer(IdKind kind, String propertyName, boolean primitive) {
            this.kind = kind;
            this.propertyName = propertyName;
            this.primitive = primitive;
        }

        @Override
        public Integer deserialize(JsonParser p, DeserializationContext ctxt) {
            String raw = p.getString();
            Integer id = raw == null ? null : kind.read(raw, propertyName);
            return id == null && primitive ? Integer.valueOf(0) : id;
        }
    }
}
