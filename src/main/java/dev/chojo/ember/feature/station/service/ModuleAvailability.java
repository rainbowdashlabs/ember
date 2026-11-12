/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.station.service;

import dev.chojo.ember.feature.cluster.repository.ClusterRepository;
import dev.chojo.ember.feature.station.entity.StationModule;
import dev.chojo.ember.feature.station.repository.StationRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.EnumSet;
import java.util.Set;

/**
 * Which modules a station has, whoever switched the others off.
 *
 * <p>The one answer every module check goes by. A module the station's cluster denies is as gone as one
 * the station switched off itself, and a module that is off takes its parts with it. Kept apart from the
 * station service because the permission resolver and the route guard ask it on every request, and
 * neither may depend on everything the station service needs.
 */
@Singleton
public class ModuleAvailability {
    private final StationRepository stationRepository;
    private final ClusterRepository clusterRepository;

    @Inject
    public ModuleAvailability(StationRepository stationRepository, ClusterRepository clusterRepository) {
        this.stationRepository = stationRepository;
        this.clusterRepository = clusterRepository;
    }

    /**
     * Every module the station does not have: its own switches, its cluster's denials, and the parts of
     * every module among them.
     *
     * @param stationId the station
     * @return the effective set of switched-off modules
     */
    public Set<StationModule> disabledAt(int stationId) {
        Set<StationModule> disabled = EnumSet.noneOf(StationModule.class);
        disabled.addAll(stationRepository.findDisabledModules(stationId));
        disabled.addAll(clusterRepository.findDeniedModulesForStation(stationId));
        return StationModule.withChildren(disabled);
    }

    /**
     * Whether the station has a module.
     *
     * @param stationId the station
     * @param module    the module or part of one
     * @return {@code true} when neither the station nor its cluster switched it or its parent off
     */
    public boolean isOn(int stationId, StationModule module) {
        return !disabledAt(stationId).contains(module);
    }
}
