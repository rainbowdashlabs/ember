/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.api;

import dev.chojo.ember.feature.station.repository.StationRepository;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.BeanDescription;
import tools.jackson.databind.SerializationConfig;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.module.SimpleModule;
import tools.jackson.databind.ser.BeanPropertyWriter;
import tools.jackson.databind.ser.ValueSerializerModifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Jackson module that converts integer stationId fields to UUID strings in JSON output.
 * Any int/Integer field named "stationId", "sourceStationId", or
 * "owningStationId" will be serialized as the station's UUID string.
 */
public class StationIdModule extends SimpleModule {
    private static final Set<String> STATION_ID_FIELDS = Set.of("stationId", "sourceStationId", "owningStationId");
    private final StationRepository stationRepository;

    public StationIdModule(StationRepository stationRepository) {
        super("StationIdModule");
        this.stationRepository = stationRepository;
    }

    @Override
    public void setupModule(SetupContext context) {
        super.setupModule(context);
        context.addSerializerModifier(new StationIdSerializerModifier(stationRepository));
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
}
