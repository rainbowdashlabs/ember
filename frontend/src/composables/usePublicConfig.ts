/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {computed} from 'vue'
import type {PublicConfigResponse} from '@/api/generated/schema'

/**
 * What the instance tells anyone who asks, before they have signed in. Any part may be missing, because
 * a request that failed is read as the empty configuration.
 */
export type PublicConfig = Partial<PublicConfigResponse>

/**
 * The public instance configuration, fetched once per request.
 *
 * A composable rather than a `useAsyncData` call per component: the key deduplicates the request,
 * but Nuxt compares the handlers behind it too and warns when two components pass their own copy
 * of the same closure. Sharing one call is what makes the key honest.
 */
export async function usePublicConfig() {
    const {data} = await useAsyncData<PublicConfig>(
        'public-config',
        () => $fetch<PublicConfig>('/api/v1/public/config').catch(() => ({} as PublicConfig)),
        {default: (): PublicConfig => ({demoUrl: '', demo: false})},
    )

    return {
        publicConfig: data,
        demoUrl: computed(() => data.value?.demoUrl ?? ''),
        isDemo: computed(() => data.value?.demo ?? false),
    }
}
