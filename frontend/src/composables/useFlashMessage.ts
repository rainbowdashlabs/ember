/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {onUnmounted, readonly, ref} from 'vue'

/**
 * Transient inline status message that clears itself after a delay. For feedback that
 * belongs inside the page flow (scanner banners, inline save confirmations); overlay
 * notifications go through useToast instead.
 */
export function useFlashMessage(defaultMs = 2500) {
    const message = ref('')
    const kind = ref<'success' | 'error'>('success')
    let timer: ReturnType<typeof setTimeout> | null = null

    function flash(text: string, flashKind: 'success' | 'error' = 'success', ms = defaultMs) {
        if (timer !== null) clearTimeout(timer)
        message.value = text
        kind.value = flashKind
        timer = setTimeout(() => {
            message.value = ''
            timer = null
        }, ms)
    }

    onUnmounted(() => {
        if (timer !== null) clearTimeout(timer)
    })

    return {message: readonly(message), kind: readonly(kind), flash}
}
