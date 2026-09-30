/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {readonly} from 'vue'
import {browserRef, browserShallowRef} from '@/util/browserState'

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

/** Why a toast is being held open: the pointer rests on it, or the focus is inside it. */
export type ToastHold = 'pointer' | 'focus'

/** How long a toast has left, what holds it open, and the timer counting down while nothing does. */
interface Countdown {
    remainingMs: number
    startedAt: number
    handle: ReturnType<typeof setTimeout> | null
    holds: Set<ToastHold>
}

/** The numbering of the toasts and the clock of each one on screen, which nothing renders. */
interface Clocks {
    nextId: number
    countdowns: Map<number, Countdown>
}

const toasts = browserRef<Toast[]>([])
const clocks = browserShallowRef<Clocks>({nextId: 0, countdowns: new Map()})

/**
 * Adds a toast to the global queue. The toast disappears automatically after {@code durationMs}
 * of being on screen unheld; {@link holdToast} stops that clock while a reader is at the toast.
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
    const id = clocks.value.nextId++
    toasts.value.push({id, message, variant, action})
    const countdown: Countdown = {remainingMs: durationMs, startedAt: 0, handle: null, holds: new Set()}
    clocks.value.countdowns.set(id, countdown)
    startCountdown(id, countdown)
}

/**
 * Stops the clock of a toast while the pointer rests on it or the focus is inside it, so a reader
 * who is still reading or about to press its action does not lose it under their hand.
 */
export function holdToast(id: number, hold: ToastHold) {
    const countdown = clocks.value.countdowns.get(id)
    if (!countdown) return
    countdown.holds.add(hold)
    if (!countdown.handle) return
    clearTimeout(countdown.handle)
    countdown.handle = null
    countdown.remainingMs -= Date.now() - countdown.startedAt
}

/** Lets go of one hold, and starts the clock again with the time it had left once nothing holds it. */
export function releaseToast(id: number, hold: ToastHold) {
    const countdown = clocks.value.countdowns.get(id)
    if (!countdown) return
    countdown.holds.delete(hold)
    if (countdown.holds.size === 0 && !countdown.handle) startCountdown(id, countdown)
}

function startCountdown(id: number, countdown: Countdown) {
    countdown.startedAt = Date.now()
    countdown.handle = setTimeout(() => dismissToast(id), Math.max(0, countdown.remainingMs))
}

/**
 * Removes the toast with the given id from the queue, if present.
 */
export function dismissToast(id: number) {
    const countdown = clocks.value.countdowns.get(id)
    if (countdown?.handle) clearTimeout(countdown.handle)
    clocks.value.countdowns.delete(id)
    toasts.value = toasts.value.filter(t => t.id !== id)
}

/**
 * Read-only view of the current toast queue; consumers render against this.
 */
export function getToasts() {
    return readonly(toasts)
}
