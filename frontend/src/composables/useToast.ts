/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import { ref, readonly } from 'vue'

export interface Toast {
    id: number
    message: string
    variant: 'info' | 'success' | 'error'
}

let nextId = 0
const toasts = ref<Toast[]>([])

export function useToast() {
    function show(message: string, variant: Toast['variant'] = 'info', durationMs = 5000) {
        const id = nextId++
        toasts.value.push({ id, message, variant })
        setTimeout(() => dismiss(id), durationMs)
    }

    function dismiss(id: number) {
        toasts.value = toasts.value.filter(t => t.id !== id)
    }

    return {
        toasts: readonly(toasts),
        show,
        dismiss,
    }
}
