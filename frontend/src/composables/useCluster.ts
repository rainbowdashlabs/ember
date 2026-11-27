/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {computed, readonly} from 'vue'
import {clusters} from '@/api'
import type {ClusterResponse} from '@/api/generated/schema'
import {chooseTabCluster, forgetTabCluster, tabClusterId} from '@/util/tabPlace'

/**
 * The clusters the reader may act for and which one they act for now, held per request.
 *
 * <p>The current cluster starts out unknown, the same on the server and in the browser; the one this
 * browser stored is put in by {@link restoreActiveCluster}, never read here.
 */
function clusterState() {
    return {
        clusterList: useState<ClusterResponse[]>('useCluster.list', () => []),
        loaded: useState('useCluster.loaded', () => false),
        currentClusterId: useState<string | null>('useCluster.current', () => null),
    }
}

/** Puts the cluster this tab acts for into the state. Called by the client plugin. */
export function restoreActiveCluster() {
    clusterState().currentClusterId.value = tabClusterId()
}

/**
 * The clusters the signed-in account may act for, and which one it is acting for now.
 *
 * <p>Modelled on {@code useStations}, and deliberately separate from it: a request can carry both contexts at
 * once, because a cluster manager who is also a member of one of its stations is one person with two hats.
 * Switching one does not disturb the other.
 *
 * <p>An account released from the last cluster it acted for stops sending that cluster once the list is
 * loaded. Take it at the top of a setup or a composable, before anything is awaited.
 */
export function useCluster() {
    const {clusterList, loaded, currentClusterId} = clusterState()

    async function load() {
        loaded.value = false
        try {
            clusterList.value = await clusters.listMine()
        } catch {
            clusterList.value = []
        }
        currentClusterId.value = tabClusterId()
        if (currentClusterId.value && !clusterList.value.some(c => c.uid === currentClusterId.value)) {
            clearActiveCluster()
        }
        loaded.value = true
    }

    function clear() {
        clusterList.value = []
        loaded.value = false
        currentClusterId.value = null
    }

    function setActiveCluster(clusterId: string) {
        chooseTabCluster(clusterId)
        currentClusterId.value = clusterId
    }

    function clearActiveCluster() {
        forgetTabCluster()
        currentClusterId.value = null
    }

    const activeCluster = computed(() => clusterList.value.find(c => c.uid === currentClusterId.value) ?? null)

    /** Whether the account can act for any cluster at all, which is what decides if the switcher appears. */
    const hasClusters = computed(() => clusterList.value.length > 0)

    return {
        clusterList: readonly(clusterList),
        loaded: readonly(loaded),
        currentClusterId: readonly(currentClusterId),
        activeCluster,
        hasClusters,
        load,
        clear,
        setActiveCluster,
        clearActiveCluster,
    }
}
