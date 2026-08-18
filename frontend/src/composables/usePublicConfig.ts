/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {computed} from 'vue'

/** What the instance tells anyone who asks, before they have signed in. */
export interface PublicConfig {
    demoUrl?: string
    demo?: boolean
}

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
