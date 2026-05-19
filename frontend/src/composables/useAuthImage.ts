/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {onUnmounted, ref, watch, type Ref} from 'vue'
import client from '@/api/client'

/**
 * Fetches an image from an authenticated endpoint and returns a blob object URL.
 * Automatically revokes old URLs and cleans up on unmount.
 */
export function useAuthImage(url: Ref<string | null | undefined> | (() => string | null | undefined)) {
    const src = ref<string | null>(null)
    const loading = ref(false)
    const failed = ref(false)

    let currentUrl: string | null = null

    function revoke() {
        if (currentUrl) {
            URL.revokeObjectURL(currentUrl)
            currentUrl = null
        }
    }

    async function load(imageUrl: string | null | undefined) {
        revoke()
        src.value = null
        failed.value = false

        if (!imageUrl) return

        loading.value = true
        try {
            const res = await client.get(imageUrl, {
                responseType: 'blob',
                validateStatus: (status) => status === 200 || status === 404,
            })
            if (res.status === 200 && res.data) {
                currentUrl = URL.createObjectURL(res.data)
                src.value = currentUrl
            } else {
                failed.value = true
            }
        } catch {
            failed.value = true
        }
        loading.value = false
    }

    if (typeof url === 'function') {
        watch(url, (newUrl) => load(newUrl), {immediate: true})
    } else {
        watch(url, (newUrl) => load(newUrl), {immediate: true})
    }

    onUnmounted(revoke)

    return {src, loading, failed}
}

/**
 * Fetches an image by building the URL from a reactive id.
 * Returns a blob object URL suitable for <img :src>.
 */
export function useAuthImageById(
    urlBuilder: (id: number) => string,
    id: Ref<number | null | undefined> | (() => number | null | undefined),
) {
    const computedUrl = typeof id === 'function'
        ? ref(id() != null ? urlBuilder(id()!) : null)
        : ref(id.value != null ? urlBuilder(id.value!) : null)

    if (typeof id === 'function') {
        watch(id, (newId) => {
            computedUrl.value = newId != null ? urlBuilder(newId) : null
        })
    } else {
        watch(id, (newId) => {
            computedUrl.value = newId != null ? urlBuilder(newId) : null
        })
    }

    return useAuthImage(computedUrl)
}
