/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.news.service;

import dev.chojo.ember.api.refusal.NewsRefusal;
import dev.chojo.ember.api.refusal.Refusal;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.entity.StationModule;
import dev.chojo.ember.feature.station.repository.StationRepository;
import dev.chojo.ember.feature.station.service.ModuleAvailability;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * Which station a public blog address names, and whether that station keeps a public blog at all.
 */
@Singleton
public class PublicBlogService {
    private final StationRepository stations;
    private final ModuleAvailability modules;

    @Inject
    public PublicBlogService(StationRepository stations, ModuleAvailability modules) {
        this.stations = stations;
        this.modules = modules;
    }

    /**
     * The station behind a public blog address, where its blog is open to the public.
     *
     * @param address  the station as the address names it, by identifier or by its public slug
     * @param missing  what to refuse with when the station is gone after its address resolved,
     *                 which each page of the blog names on its own
     * @param closed   what to refuse with when the station keeps no public blog, or no news at all because
     *                 it or its cluster switched them off
     */
    public Station openBlog(String address, Refusal missing, Refusal closed) {
        int stationId =
                stations.resolveAddressedId(address).orElseThrow(NewsRefusal.STATION_NOT_HERE_BEHIND_BLOG::raise);
        var station = stations.findById(stationId).orElseThrow(missing::raise);
        if (!station.publicBlogEnabled()) throw closed.raise();
        if (StationModule.NEWS.isOffIn(modules.disabledAt(stationId))) throw closed.raise();
        return station;
    }
}
