/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {computed, onMounted, ref} from 'vue'
import {getPushKey} from '@/api/push'
import type {PushKey} from '@/api/generated/schema'

/**
 * Whether this instance sends pushes, and the key a browser subscribes with, loaded once the page is mounted.
 * A key that cannot be fetched counts as push being off, since nothing on the page can do without it.
 */
export function usePushAvailability() {
    const key = ref<PushKey | null>(null)

    onMounted(async () => {
        try {
            key.value = await getPushKey()
        } catch {
            key.value = null
        }
    })

    return {
        key,
        available: computed(() => key.value?.enabled === true),
    }
}
