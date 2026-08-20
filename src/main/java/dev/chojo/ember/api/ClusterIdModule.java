/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.api;

import dev.chojo.ember.feature.cluster.entity.Cluster;
import dev.chojo.ember.feature.cluster.repository.ClusterRepository;
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
import java.util.Set;
import java.util.UUID;

/**
 * Converts between the internal {@code int} cluster id used inside the backend and the identity the API
 * exposes, exactly as {@link StationIdModule} does for stations: ints inside, UUIDs on the wire.
 *
 * <p>Both halves matter. Without the deserialising one, a body the API itself produced would come back
 * carrying a UUID string and fail to coerce into an int.
 */
public class ClusterIdModule extends SimpleModule {
    private static final Set<String> CLUSTER_ID_FIELDS = Set.of("clusterId", "ownerClusterId");
    private final ClusterRepository clusterRepository;

    public ClusterIdModule(ClusterRepository clusterRepository) {
        super("ClusterIdModule");
        this.clusterRepository = clusterRepository;
    }

    @Override
    public void setupModule(SetupContext context) {
        super.setupModule(context);
        context.addSerializerModifier(new ClusterIdSerializerModifier(clusterRepository));
        context.addDeserializerModifier(new ClusterIdDeserializerModifier(clusterRepository));
    }

    private static class ClusterIdSerializerModifier extends ValueSerializerModifier {
        private final ClusterRepository clusterRepository;

        ClusterIdSerializerModifier(ClusterRepository clusterRepository) {
            this.clusterRepository = clusterRepository;
        }

        @Override
        public List<BeanPropertyWriter> changeProperties(
                SerializationConfig config,
                BeanDescription.Supplier beanDescSupplier,
                List<BeanPropertyWriter> beanProperties) {
            var result = new ArrayList<BeanPropertyWriter>(beanProperties.size());
            for (var prop : beanProperties) {
                if (CLUSTER_ID_FIELDS.contains(prop.getName()) && isIntType(prop)) {
                    result.add(new ClusterIdPropertyWriter(prop, clusterRepository));
                } else {
                    result.add(prop);
                }
            }
            return result;
        }

        private boolean isIntType(BeanPropertyWriter prop) {
            var type = prop.getType().getRawClass();
            return type == int.class || type == Integer.class;
        }
    }

    private static class ClusterIdPropertyWriter extends BeanPropertyWriter {
        private final ClusterRepository clusterRepository;

        ClusterIdPropertyWriter(BeanPropertyWriter base, ClusterRepository clusterRepository) {
            super(base);
            this.clusterRepository = clusterRepository;
        }

        @Override
        public void serializeAsProperty(Object bean, JsonGenerator gen, SerializationContext ctxt) throws Exception {
            Object value = get(bean);
            if (value == null) {
                gen.writeName(getName());
                gen.writeNull();
                return;
            }
            int clusterId = (value instanceof Integer i) ? i : ((Number) value).intValue();
            UUID uid = clusterRepository.resolveUid(clusterId);
            gen.writeName(getName());
            if (uid != null) {
                gen.writeString(uid.toString());
            } else {
                gen.writeNull();
            }
        }
    }

    private static class ClusterIdDeserializerModifier extends ValueDeserializerModifier {
        private final ClusterRepository clusterRepository;

        ClusterIdDeserializerModifier(ClusterRepository clusterRepository) {
            this.clusterRepository = clusterRepository;
        }

        @Override
        public BeanDeserializerBuilder updateBuilder(
                DeserializationConfig config,
                BeanDescription.Supplier beanDescSupplier,
                BeanDeserializerBuilder builder) {
            // Collect replacements first so we do not mutate while iterating
            List<SettableBeanProperty> replacements = new ArrayList<>();
            Iterator<SettableBeanProperty> it = builder.getProperties();
            while (it.hasNext()) {
                SettableBeanProperty prop = it.next();
                if (!CLUSTER_ID_FIELDS.contains(prop.getName())) continue;
                Class<?> raw = prop.getType().getRawClass();
                if (raw != int.class && raw != Integer.class) continue;
                replacements.add(prop.withValueDeserializer(
                        new UuidStringToIntDeserializer(clusterRepository, prop.getName(), raw == int.class)));
            }
            for (SettableBeanProperty replacement : replacements) {
                builder.addOrReplaceProperty(replacement, true);
            }
            return builder;
        }
    }

    private static class UuidStringToIntDeserializer extends ValueDeserializer<Integer> {
        private final ClusterRepository clusterRepository;
        private final String propertyName;
        private final boolean primitive;

        UuidStringToIntDeserializer(ClusterRepository clusterRepository, String propertyName, boolean primitive) {
            this.clusterRepository = clusterRepository;
            this.propertyName = propertyName;
            this.primitive = primitive;
        }

        @Override
        public Integer deserialize(JsonParser p, DeserializationContext ctxt) {
            String raw = p.getString();
            if (raw == null) {
                if (primitive) return 0;
                return null;
            }
            UUID uid;
            try {
                uid = UUID.fromString(raw);
            } catch (IllegalArgumentException ex) {
                throw new IllegalArgumentException(
                        "Cannot parse cluster id '" + propertyName + "': '" + raw + "' is not a UUID");
            }
            return clusterRepository
                    .findByUid(uid)
                    .map(Cluster::id)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Unknown cluster id '" + propertyName + "' (uid " + uid + ")"));
        }
    }
}
