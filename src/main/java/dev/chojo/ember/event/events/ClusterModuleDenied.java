/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.event.events;

import dev.chojo.ember.event.DomainEvent;
import dev.chojo.ember.feature.station.entity.StationModule;

/**
 * The cluster has switched off something the station was using.
 *
 * <p>Only raised for stations that actually had it on. A page vanishing without explanation is the kind of
 * thing people report as a fault, so the ones who lose something hear why, and the ones who never had it are
 * not told about a change that means nothing to them.
 *
 * @param stationId   the station losing it
 * @param clusterName the cluster that decided, for the reader
 * @param module      what was switched off
 */
public record ClusterModuleDenied(int stationId, String clusterName, StationModule module) implements DomainEvent {}
