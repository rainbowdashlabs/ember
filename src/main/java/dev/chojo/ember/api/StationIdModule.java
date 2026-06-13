/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.api;

import dev.chojo.ember.feature.station.repository.StationRepository;
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
 * Jackson module that converts between the internal {@code int} station id used everywhere
 * inside the backend and the public-facing UUID string exposed by the API. Any
 * {@code int}/{@code Integer} field named {@code stationId}, {@code sourceStationId},
 * {@code partnerStationId}, or {@code owningStationId} is:
 * <ul>
 *   <li>serialized as the station's UUID string when written to JSON, and</li>
 *   <li>deserialized from a UUID string back into the internal int when read from JSON.</li>
 * </ul>
 * Without the deserialize half, inbound request bodies that the API itself produced (and that
 * therefore carry the public UUID string) would fail with an {@code InvalidFormatException}
 * when Jackson tried to coerce the string into an {@code int}.
 */
public class StationIdModule extends SimpleModule {
    private static final Set<String> STATION_ID_FIELDS =
            Set.of("stationId", "sourceStationId", "partnerStationId", "owningStationId");
    private final StationRepository stationRepository;

    public StationIdModule(StationRepository stationRepository) {
        super("StationIdModule");
        this.stationRepository = stationRepository;
    }

    @Override
    public void setupModule(SetupContext context) {
        super.setupModule(context);
        context.addSerializerModifier(new StationIdSerializerModifier(stationRepository));
        context.addDeserializerModifier(new StationIdDeserializerModifier(stationRepository));
    }

    private static class StationIdSerializerModifier extends ValueSerializerModifier {
        private final StationRepository stationRepository;

        StationIdSerializerModifier(StationRepository stationRepository) {
            this.stationRepository = stationRepository;
        }

        @Override
        public List<BeanPropertyWriter> changeProperties(
                SerializationConfig config,
                BeanDescription.Supplier beanDescSupplier,
                List<BeanPropertyWriter> beanProperties) {
            var result = new ArrayList<BeanPropertyWriter>(beanProperties.size());
            for (var prop : beanProperties) {
                if (STATION_ID_FIELDS.contains(prop.getName()) && isIntType(prop)) {
                    result.add(new StationIdPropertyWriter(prop, stationRepository));
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

    private static class StationIdPropertyWriter extends BeanPropertyWriter {
        private final StationRepository stationRepository;

        StationIdPropertyWriter(BeanPropertyWriter base, StationRepository stationRepository) {
            super(base);
            this.stationRepository = stationRepository;
        }

        @Override
        public void serializeAsProperty(Object bean, JsonGenerator gen, SerializationContext ctxt) throws Exception {
            Object value = get(bean);
            if (value == null) {
                gen.writeName(getName());
                gen.writeNull();
                return;
            }
            int stationId = (value instanceof Integer i) ? i : ((Number) value).intValue();
            UUID uid = stationRepository.resolveUid(stationId);
            gen.writeName(getName());
            if (uid != null) {
                gen.writeString(uid.toString());
            } else {
                gen.writeNull();
            }
        }
    }

    /**
     * Walks the properties of every {@code BeanDeserializer} and swaps the deserializer of any
     * matching field so an inbound UUID string is resolved back into the internal int.
     */
    private static class StationIdDeserializerModifier extends ValueDeserializerModifier {
        private final StationRepository stationRepository;

        StationIdDeserializerModifier(StationRepository stationRepository) {
            this.stationRepository = stationRepository;
        }

        @Override
        public BeanDeserializerBuilder updateBuilder(
                DeserializationConfig config,
                BeanDescription.Supplier beanDescSupplier,
                BeanDeserializerBuilder builder) {
            // Collect replacements first so we don't mutate while iterating.
            List<SettableBeanProperty> replacements = new ArrayList<>();
            Iterator<SettableBeanProperty> it = builder.getProperties();
            while (it.hasNext()) {
                SettableBeanProperty prop = it.next();
                if (!STATION_ID_FIELDS.contains(prop.getName())) continue;
                Class<?> raw = prop.getType().getRawClass();
                if (raw != int.class && raw != Integer.class) continue;
                replacements.add(prop.withValueDeserializer(
                        new UuidStringToIntDeserializer(stationRepository, prop.getName(), raw == int.class)));
            }
            for (SettableBeanProperty replacement : replacements) {
                builder.addOrReplaceProperty(replacement, true);
            }
            return builder;
        }
    }

    /**
     * Deserializes a JSON string (the station UUID) into the internal int station id.
     * Accepts {@code null} only when the property is {@code Integer} (boxed); for primitive
     * int properties a null incoming value triggers a clear error from the calling context.
     */
    private static class UuidStringToIntDeserializer extends ValueDeserializer<Integer> {
        private final StationRepository stationRepository;
        private final String propertyName;
        private final boolean primitive;

        UuidStringToIntDeserializer(StationRepository stationRepository, String propertyName, boolean primitive) {
            this.stationRepository = stationRepository;
            this.propertyName = propertyName;
            this.primitive = primitive;
        }

        @Override
        public Integer deserialize(JsonParser p, DeserializationContext ctxt) {
            String raw = p.getString();
            if (raw == null) {
                if (primitive) return 0; // matches Jackson's default for null → primitive int
                return null;
            }
            UUID uid;
            try {
                uid = UUID.fromString(raw);
            } catch (IllegalArgumentException ex) {
                throw new IllegalArgumentException(
                        "Cannot parse station id '" + propertyName + "': '" + raw + "' is not a UUID");
            }
            return stationRepository
                    .findByUid(uid)
                    .map(s -> s.id())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Unknown station id '" + propertyName + "' (uid " + uid + ")"));
        }
    }
}
