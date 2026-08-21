/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.cluster;

import com.google.inject.AbstractModule;
import com.google.inject.multibindings.Multibinder;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.feature.cluster.route.ClusterNotificationRoutes;
import dev.chojo.ember.feature.cluster.route.ClusterRoutes;
import dev.chojo.ember.feature.cluster.route.ClusterStationRoutes;
import dev.chojo.ember.feature.cluster.route.StationClusterRoutes;

/**
 * The cluster's own wiring, kept beside the station's rather than folded into it.
 *
 * <p>A cluster is a whole second context with its own members, permissions and screens. Binding it here means
 * the set of things that arrive with a cluster is one file rather than a scatter of lines through the module
 * that wires everything else.
 */
public class ClusterModule extends AbstractModule {

    @Override
    protected void configure() {
        Multibinder<Routes> routesBinder = Multibinder.newSetBinder(binder(), Routes.class);
        routesBinder.addBinding().to(ClusterRoutes.class);
        routesBinder.addBinding().to(ClusterStationRoutes.class);
        routesBinder.addBinding().to(ClusterNotificationRoutes.class);
        routesBinder.addBinding().to(StationClusterRoutes.class);
    }
}
