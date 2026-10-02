/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type {Ref} from 'vue'
import {browserRef, browserShallowRef} from '@/util/browserState'
import client from '@/api/client'
import type {PublicMapsConfig} from '@/api/generated/schema'

const cache = browserRef<PublicMapsConfig | null>(null)
const inFlight = browserShallowRef<Promise<PublicMapsConfig> | null>(null)

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
        if (inFlight.value) return inFlight.value
        const request = (async () => {
            try {
                const res = await client.get<PublicMapsConfig>('/public/settings/maps')
                cache.value = res.data
                return res.data
            } finally {
                inFlight.value = null
            }
        })()
        inFlight.value = request
        return request
    }

    async function reload(): Promise<PublicMapsConfig> {
        cache.value = null
        return load()
    }

    return {config: cache, load, reload}
}
