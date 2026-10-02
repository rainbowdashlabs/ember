/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.api;

import dev.chojo.ember.feature.cluster.repository.ClusterRepository;
import dev.chojo.ember.feature.station.repository.StationRepository;
import io.javalin.json.JavalinJackson3;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JacksonModule;
import tools.jackson.databind.json.JsonMapper;

import java.text.SimpleDateFormat;

/**
 * The one configuration of the JSON mapper the API reads and writes with: ISO date formatting and the
 * translation of internal station and cluster ids to their public UUIDs.
 *
 * <p>The server, the route tests and the generator of the API description all build their mapper here,
 * so the shape they see cannot fork from the shape the API sends.
 *
 * <p>{@code FAIL_ON_UNKNOWN_PROPERTIES} is Jackson's default but pinned explicitly, so an inbound
 * payload with extra fields is rejected with 400 rather than silently dropped, even if somebody copies
 * this configuration from a lenient mapper such as the federation client's.
 */
public final class ApiJsonMapper {

    private ApiJsonMapper() {}

    /**
     * The mapper the API serves with, translating station and cluster ids through the given stores.
     *
     * @param stationRepository resolves the station ids written as addresses
     * @param clusterRepository resolves the cluster ids written as addresses
     * @return the mapper, wrapped for Javalin
     */
    public static JavalinJackson3 forApi(StationRepository stationRepository, ClusterRepository clusterRepository) {
        return new JavalinJackson3(create(PublicIdModule.forApi(stationRepository, clusterRepository)), false);
    }

    /**
     * The API's mapper with the given modules. The server passes the id translation; a reader that only
     * introspects the types, and applies the id rule by name, passes none.
     *
     * @param modules the modules to add
     * @return the mapper
     */
    public static JsonMapper create(JacksonModule... modules) {
        return JsonMapper.builder()
                .addModules(modules)
                .disable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .defaultDateFormat(new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSX"))
                .build();
    }
}
