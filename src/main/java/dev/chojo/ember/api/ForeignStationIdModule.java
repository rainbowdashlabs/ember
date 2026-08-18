/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.api;

import tools.jackson.core.JsonParser;
import tools.jackson.databind.BeanDescription;
import tools.jackson.databind.DeserializationConfig;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.deser.BeanDeserializerBuilder;
import tools.jackson.databind.deser.SettableBeanProperty;
import tools.jackson.databind.deser.ValueDeserializerModifier;
import tools.jackson.databind.module.SimpleModule;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * Jackson module for reading responses served by a <em>federation partner</em>.
 *
 * <p>A partner serialises its entities through the API boundary, where {@link StationIdModule}
 * rewrites the internal {@code int} station id into the station's public UUID. Reading such a
 * response back into the same entity therefore hands Jackson a UUID string where an {@code int}
 * is declared, which is a hard failure.
 *
 * <p>A partner's internal station id means nothing on this instance, so every station-id field is
 * read as {@code 0} rather than resolved. Resolving it would be worse than useless: a partner
 * could name any station on this instance and have its id land inside an entity we then trust.
 */
public class ForeignStationIdModule extends SimpleModule {
    private static final Set<String> STATION_ID_FIELDS =
            Set.of("stationId", "sourceStationId", "partnerStationId", "owningStationId");

    public ForeignStationIdModule() {
        super("ForeignStationIdModule");
    }

    @Override
    public void setupModule(SetupContext context) {
        super.setupModule(context);
        context.addDeserializerModifier(new ForeignStationIdDeserializerModifier());
    }

    private static class ForeignStationIdDeserializerModifier extends ValueDeserializerModifier {
        @Override
        public BeanDeserializerBuilder updateBuilder(
                DeserializationConfig config,
                BeanDescription.Supplier beanDescSupplier,
                BeanDeserializerBuilder builder) {
            List<SettableBeanProperty> replacements = new ArrayList<>();
            Iterator<SettableBeanProperty> it = builder.getProperties();
            while (it.hasNext()) {
                SettableBeanProperty prop = it.next();
                if (!STATION_ID_FIELDS.contains(prop.getName())) continue;
                Class<?> raw = prop.getType().getRawClass();
                if (raw != int.class && raw != Integer.class) continue;
                replacements.add(prop.withValueDeserializer(new ForeignStationIdDeserializer(raw == int.class)));
            }
            for (SettableBeanProperty replacement : replacements) {
                builder.addOrReplaceProperty(replacement, true);
            }
            return builder;
        }
    }

    /**
     * Reads whatever a partner sent for a station id - a UUID string, a number, or null - and
     * answers the neutral local value.
     */
    private static class ForeignStationIdDeserializer extends ValueDeserializer<Integer> {
        private final boolean primitive;

        ForeignStationIdDeserializer(boolean primitive) {
            this.primitive = primitive;
        }

        @Override
        public Integer deserialize(JsonParser p, DeserializationContext ctxt) {
            return primitive ? 0 : null;
        }
    }
}
