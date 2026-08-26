/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {computed, readonly, ref} from 'vue'
import {clusters} from '@/api'
import type {Cluster} from '@/api/clusters'
import {getItem, removeItem, setItem} from '@/api/storage'

/**
 * The clusters the signed-in account may act for, and which one it is acting for now.
 *
 * <p>Modelled on {@code useStations}, and deliberately separate from it: a request can carry both contexts at
 * once, because a cluster manager who is also a member of one of its stations is one person with two hats.
 * Switching one does not disturb the other.
 */
const clusterList = ref<Cluster[]>([])
const loaded = ref(false)
const currentClusterId = ref<string | null>(getItem('cluster_id') ?? null)

export function useCluster() {
    async function load() {
        loaded.value = false
        try {
            clusterList.value = await clusters.listMine()
        } catch {
            clusterList.value = []
        }
        currentClusterId.value = getItem('cluster_id') ?? null
        // An account released from the last cluster it acted for should not keep sending its identity
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
        setItem('cluster_id', clusterId)
        currentClusterId.value = clusterId
    }

    function clearActiveCluster() {
        removeItem('cluster_id')
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
