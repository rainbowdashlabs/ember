/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {computed} from 'vue'
import {useAbsoluteUrl} from '@/util/socialMeta'

/**
 * The one address a page is to be indexed under, and the address its preview card points back at.
 *
 * <p>The origin comes from the same place every other outward address on the page comes from. It
 * used to be read once when this module was first loaded, which on the server is before any request
 * has arrived: there was no browser to ask and nothing configured to fall back on, so every page
 * rendered on the server named itself with a path alone, which is not an address anything outside
 * can follow.
 *
 * @param path the page's own path, or a function giving it where it changes with the route
 */
export function useCanonical(path: string | (() => string)) {
    const absolute = useAbsoluteUrl()

    const url = computed(() => absolute(typeof path === 'function' ? path() : path) ?? '')

    useHead(computed(() => ({
        link: [{rel: 'canonical', href: url.value}],
        meta: [{property: 'og:url', content: url.value}],
    })))

    return url
}
