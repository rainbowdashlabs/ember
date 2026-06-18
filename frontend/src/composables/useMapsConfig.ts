/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {ref, type Ref} from 'vue'
import client from '@/api/client'

export type MapTileProvider = 'OSM' | 'MAPBOX' | 'STADIA' | 'MAPTILER' | 'THUNDERFOREST' | 'CUSTOM'

export interface PublicMapsConfig {
    provider: MapTileProvider
    urlTemplate: string
    attribution: string
    minZoom: number
    maxZoom: number
}

const cache = ref<PublicMapsConfig | null>(null)
let inFlight: Promise<PublicMapsConfig> | null = null

/**
 * Fetches the instance-wide public maps config once per session and caches it. Multiple
 * callers during the initial load share the same promise so we never fan out the request.
 */
export function useMapsConfig(): {
    config: Ref<PublicMapsConfig | null>
    load: () => Promise<PublicMapsConfig>
    reload: () => Promise<PublicMapsConfig>
} {
    async function load(): Promise<PublicMapsConfig> {
        if (cache.value) return cache.value
        if (inFlight) return inFlight
        inFlight = (async () => {
            try {
                const res = await client.get<PublicMapsConfig>('/public/settings/maps')
                cache.value = res.data
                return res.data
            } finally {
                inFlight = null
            }
        })()
        return inFlight
    }

    async function reload(): Promise<PublicMapsConfig> {
        cache.value = null
        return load()
    }

    return {config: cache, load, reload}
}
