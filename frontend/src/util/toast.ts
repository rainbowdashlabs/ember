/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {readonly, ref} from 'vue'

/** Something the reader may do about what the toast says, offered beside it. */
export interface ToastAction {
    label: string
    run: () => void | Promise<void>
}

export interface Toast {
    id: number
    message: string
    variant: 'info' | 'success' | 'error'
    action?: ToastAction
}

let nextId = 0
const toasts = ref<Toast[]>([])

/**
 * Adds a toast to the global queue. The toast disappears automatically after {@code durationMs}.
 *
 * <p>An action goes beside the message where the reader may still do something about it, which is how
 * an undo reaches somebody who has already scrolled away from the row they pressed. Taking the action
 * closes the toast: whatever it was offering has happened.
 */
export function showToast(
    message: string,
    variant: Toast['variant'] = 'info',
    durationMs = 5000,
    action?: ToastAction,
) {
    const id = nextId++
    toasts.value.push({id, message, variant, action})
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
