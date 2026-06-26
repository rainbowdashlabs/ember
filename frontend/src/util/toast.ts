/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {readonly, ref} from 'vue'

export interface Toast {
    id: number
    message: string
    variant: 'info' | 'success' | 'error'
}

let nextId = 0
const toasts = ref<Toast[]>([])

/**
 * Adds a toast to the global queue. The toast disappears automatically after {@code durationMs}.
 */
export function showToast(message: string, variant: Toast['variant'] = 'info', durationMs = 5000) {
    const id = nextId++
    toasts.value.push({id, message, variant})
    setTimeout(() => dismissToast(id), durationMs)
}

/**
 * Removes the toast with the given id from the queue, if present.
 */
export function dismissToast(id: number) {
    toasts.value = toasts.value.filter(t => t.id !== id)
}

/**
 * Read-only view of the current toast queue; consumers render against this.
 */
export function getToasts() {
    return readonly(toasts)
}
