/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {getItem, getTabItem, removeItem, removeTabItem, setItem, setTabItem} from '@/api/storage'

/**
 * The station and the cluster this browser tab works in.
 *
 * <p>Each tab keeps its own in the tab's session storage, so a switch in one tab leaves every other tab
 * where it is, and a reload keeps the tab where it was. The station and cluster chosen last in any tab are
 * kept in the storage all tabs share as well. A tab without one of its own starts there and takes it as
 * its own on first use, so a later switch in another tab no longer reaches it.
 *
 * <p>Read these instead of the stored {@code station_id} and {@code cluster_id}: those only hold where a
 * new tab starts.
 */

/** The station this tab works in, or `null` where neither this tab nor the browser has one. */
export function tabStationId(): string | null {
    const own = getTabItem('tab_station_id')
    if (own !== null) return own
    const last = getItem('station_id')
    if (last !== null) setTabItem('tab_station_id', last)
    return last
}

/** Moves this tab to a station, which is also where the next new tab starts. */
export function chooseTabStation(stationId: string): void {
    setTabItem('tab_station_id', stationId)
    setItem('station_id', stationId)
}

/**
 * Leaves this tab without a station. Where a new tab starts is forgotten too, unless another tab chose
 * a station since, so a tab dropping a station it lost leaves the choice of another tab alone.
 */
export function forgetTabStation(): void {
    const own = getTabItem('tab_station_id')
    removeTabItem('tab_station_id')
    if (own === null || getItem('station_id') === own) removeItem('station_id')
}

/** The cluster this tab acts for, or `null` where neither this tab nor the browser has one. */
export function tabClusterId(): string | null {
    const own = getTabItem('tab_cluster_id')
    if (own !== null) return own
    const last = getItem('cluster_id')
    if (last !== null) setTabItem('tab_cluster_id', last)
    return last
}

/** Moves this tab to a cluster, which is also where the next new tab starts. */
export function chooseTabCluster(clusterId: string): void {
    setTabItem('tab_cluster_id', clusterId)
    setItem('cluster_id', clusterId)
}

/**
 * Leaves this tab without a cluster. Where a new tab starts is forgotten too, unless another tab chose
 * a cluster since, so a tab dropping a cluster it lost leaves the choice of another tab alone.
 */
export function forgetTabCluster(): void {
    const own = getTabItem('tab_cluster_id')
    removeTabItem('tab_cluster_id')
    if (own === null || getItem('cluster_id') === own) removeItem('cluster_id')
}

/** Forgets the station and the cluster of this tab and of the browser, as on sign-out. */
export function forgetTabPlaces(): void {
    removeTabItem('tab_station_id')
    removeTabItem('tab_cluster_id')
    removeItem('station_id')
    removeItem('cluster_id')
}
