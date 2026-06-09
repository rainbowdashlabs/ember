/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {computed} from 'vue'

const siteUrl = import.meta.env.NUXT_PUBLIC_SITE_URL as string || (typeof window !== 'undefined' ? window.location.origin : '')

export function useCanonical(path: string | (() => string)) {
    const url = computed(() => {
        const p = typeof path === 'function' ? path() : path
        return `${siteUrl}${p}`
    })

    useHead(computed(() => ({
        link: [{rel: 'canonical', href: url.value}],
        meta: [{property: 'og:url', content: url.value}],
    })))

    return url
}
