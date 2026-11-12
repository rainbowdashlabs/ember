/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.service;

import dev.chojo.ember.api.auth.ClusterPermission;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.feature.members.entity.PermissionNode;
import dev.chojo.ember.feature.station.entity.StationModule;
import dev.chojo.ember.feature.station.service.ModuleAvailability;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * The permission hierarchies the permission picker draws.
 *
 * <p>For a station, the permissions of a module it switched off are left out, children included, so the
 * picker offers nothing that would grant nothing. Grants of them that are already stored stay stored.
 */
@Singleton
public class PermissionHierarchyService {
    private final ModuleAvailability modules;

    @Inject
    public PermissionHierarchyService(ModuleAvailability modules) {
        this.modules = modules;
    }

    /**
     * The station permissions with their direct children.
     *
     * @param stationId the station the picker is drawn for, or {@code null} for every permission there is
     * @return the permissions the station has, each with the children it has
     */
    public List<PermissionNode> stationHierarchy(@Nullable Integer stationId) {
        Set<StationModule> disabled = stationId == null ? Set.of() : modules.disabledAt(stationId);
        return Arrays.stream(StationPermission.values())
                .filter(permission -> !permission.isOffIn(disabled))
                .map(permission -> new PermissionNode(
                        permission.name(),
                        Arrays.stream(permission.getChildren())
                                .filter(child -> !child.isOffIn(disabled))
                                .map(Enum::name)
                                .toList()))
                .toList();
    }

    /**
     * The cluster permissions with their direct children. A cluster switches no module off for itself.
     *
     * @return every cluster permission
     */
    public List<PermissionNode> clusterHierarchy() {
        return Arrays.stream(ClusterPermission.values())
                .map(permission -> new PermissionNode(
                        permission.name(),
                        Arrays.stream(permission.getChildren()).map(Enum::name).toList()))
                .toList();
    }
}
